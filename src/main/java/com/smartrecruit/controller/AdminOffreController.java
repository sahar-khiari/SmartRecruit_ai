package com.smartrecruit.controller;

import com.smartrecruit.dto.request.OffreRechercheCriteres;
import com.smartrecruit.dto.request.OffreSuspensionRequest;
import com.smartrecruit.dto.response.OffreEmploiResponse;
import com.smartrecruit.dto.response.OffreStatistiquesResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.StatutOffre;
import com.smartrecruit.service.CurrentUserService;
import com.smartrecruit.service.OffreEmploiService;
import com.smartrecruit.service.OffreGestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Function;

/**
 * Module 2 : modération des offres par l'administrateur.
 * L'admin consulte, suspend, réactive ou supprime, mais ne modifie jamais le contenu d'une offre.
 * Accès ADMIN garanti par la règle /api/admin/** de SecurityConfig.
 */
@Tag(name = "Admin - modération des offres")
@RestController
@RequestMapping("/api/admin/offres")
@RequiredArgsConstructor
public class AdminOffreController {

    private final OffreGestionService offreGestionService;
    private final OffreEmploiService offreEmploiService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Lister toutes les offres avec filtres")
    @GetMapping
    public ResponseEntity<PageResponse<OffreEmploiResponse>> lister(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatutOffre statut,
            @RequestParam(required = false) Long recruteurId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        OffreRechercheCriteres criteres = new OffreRechercheCriteres(
                q, null, null, null, null, null, null, statut, recruteurId);
        return ResponseEntity.ok(PageResponse.from(
                offreGestionService.rechercherToutesOffres(criteres, pageable), Function.identity()));
    }

    @Operation(summary = "Statistiques des offres par statut")
    @GetMapping("/statistiques")
    public ResponseEntity<OffreStatistiquesResponse> statistiques() {
        return ResponseEntity.ok(offreGestionService.statistiques());
    }

    @Operation(summary = "Détail d'une offre (lecture seule)")
    @GetMapping("/{id}")
    public ResponseEntity<OffreEmploiResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(offreEmploiService.getById(id));
    }

    @Operation(summary = "Suspendre une offre avec un motif")
    @PutMapping("/{id}/suspendre")
    public ResponseEntity<OffreEmploiResponse> suspendre(@PathVariable Long id,
                                                         @Valid @RequestBody OffreSuspensionRequest request) {
        return ResponseEntity.ok(offreGestionService.suspendre(id, request.motif()));
    }

    @Operation(summary = "Réactiver une offre suspendue")
    @PutMapping("/{id}/reactiver")
    public ResponseEntity<OffreEmploiResponse> reactiver(@PathVariable Long id) {
        return ResponseEntity.ok(offreGestionService.reactiver(id));
    }

    @Operation(summary = "Supprimer une offre abusive")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        offreEmploiService.delete(id, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
