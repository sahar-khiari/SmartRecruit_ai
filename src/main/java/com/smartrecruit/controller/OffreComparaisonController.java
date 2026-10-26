package com.smartrecruit.controller;

import com.smartrecruit.dto.request.OffreComparaisonRequest;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.dto.response.OffreComparaisonResponse;
import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.integration.ai.OffreComparaisonClient;
import com.smartrecruit.repository.OffreEmploiRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module 2 : comparaison IA d'offres pour le candidat (Cohere via FastAPI).
 * Les offres sont relues en base (seules les offres actives sont comparables) : le front n'envoie que leurs id.
 */
@Tag(name = "Offres - comparaison IA (Cohere)")
@RestController
@RequestMapping(value = "/api/offres", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CANDIDATE')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class OffreComparaisonController {

    private final OffreEmploiRepository offreRepository;
    private final OffreComparaisonClient comparaisonClient;

    @Operation(summary = "Comparer 2 ou 3 offres avec l'IA",
            description = "Tableau comparatif (missions, niveau, compétences, contrat et lieu), points forts et "
                    + "recommandation selon le profil facultatif du candidat, anonymisé avant l'envoi à l'IA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comparaison"),
            @ApiResponse(responseCode = "400", description = "Moins de 2 ou plus de 3 offres, ou offres identiques",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Offre introuvable ou fermée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "IA indisponible",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/comparer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OffreComparaisonResponse comparer(@Valid @RequestBody OffreComparaisonRequest request) {
        return comparaisonClient.comparer(construireRequete(request));
    }

    /** Relit les offres en base avant l'appel à l'IA (qui peut durer ~50 s, hors transaction). */
    private Map<String, Object> construireRequete(OffreComparaisonRequest request) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(request.offreIds()));
        if (ids.size() < 2) {
            throw new IllegalArgumentException("Choisissez au moins 2 offres différentes");
        }
        Map<Long, OffreEmploi> trouvees = offreRepository.findAllById(ids).stream()
                .filter(OffreEmploi::isActive)
                .collect(Collectors.toMap(OffreEmploi::getId, Function.identity()));

        List<Map<String, Object>> offres = ids.stream().map(id -> {
            OffreEmploi offre = trouvees.get(id);
            if (offre == null) {
                throw new ResourceNotFoundException("Offre introuvable ou fermée : " + id);
            }
            Map<String, Object> o = new HashMap<>();
            o.put("id", offre.getId());
            o.put("titrePoste", offre.getTitrePoste());
            o.put("description", offre.getDescription());
            o.put("niveauRequis", offre.getNiveauRequis());
            o.put("competencesRequises", offre.getCompetencesRequises());
            o.put("localisation", offre.getLocalisation());
            o.put("typeContrat", offre.getTypeContrat());
            return o;
        }).toList();

        Map<String, Object> body = new HashMap<>();
        body.put("offres", offres);
        if (request.profil() != null && !request.profil().isBlank()) {
            body.put("profil", request.profil().trim());
        }
        return body;
    }
}
