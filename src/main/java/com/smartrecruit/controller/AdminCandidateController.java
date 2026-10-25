package com.smartrecruit.controller;

import com.smartrecruit.dto.request.AdminCandidateCreateRequest;
import com.smartrecruit.dto.request.CandidateProfileUpdateRequest;
import com.smartrecruit.dto.request.CandidateSkillsUpdateRequest;
import com.smartrecruit.dto.response.CandidateProfileResponse;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.dto.response.MessageResponse;
import com.smartrecruit.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/admin/candidates", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration des candidats", description = "Création, modification et suppression des candidats (ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class AdminCandidateController {

    private final CandidateService candidateService;

    @Operation(summary = "Créer un candidat",
            description = "Crée un compte CANDIDATE et, si fourni, son profil métier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Candidat créé"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email déjà utilisé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateProfileResponse createCandidate(@Valid @RequestBody AdminCandidateCreateRequest request) {
        return candidateService.createCandidate(request);
    }

    @Operation(summary = "Modifier le profil d'un candidat")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil enregistré"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Candidat introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CandidateProfileResponse updateCandidate(@PathVariable Long id,
                                                    @Valid @RequestBody CandidateProfileUpdateRequest request) {
        return candidateService.updateCandidate(id, request);
    }

    @Operation(summary = "Remplacer les compétences d'un candidat")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compétences enregistrées"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Candidat introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}/skills", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CandidateProfileResponse updateCandidateSkills(@PathVariable Long id,
                                                          @Valid @RequestBody CandidateSkillsUpdateRequest request) {
        return candidateService.updateCandidateSkills(id, request);
    }

    @Operation(summary = "Supprimer un candidat",
            description = "Supprime le compte, le profil et les compétences.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidat supprimé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "404", description = "Candidat introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Candidat lié à des candidatures",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public MessageResponse deleteCandidate(@PathVariable Long id) {
        candidateService.deleteCandidate(id);
        return new MessageResponse("Candidat supprimé avec succès");
    }
}
