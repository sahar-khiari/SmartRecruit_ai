package com.smartrecruit.controller;

import com.smartrecruit.dto.request.OffreRechercheCriteres;
import com.smartrecruit.dto.response.OffreEmploiResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.StatutOffre;
import com.smartrecruit.service.CurrentUserService;
import com.smartrecruit.service.OffreGestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Function;

/**
 * Module 2 : recherche multicritère et cycle de vie des offres (recruteur / candidat).
 */
@Tag(name = "Offres - recherche et cycle de vie")
@RestController
@RequestMapping("/api/offres")
@RequiredArgsConstructor
public class OffreGestionController {

    private final OffreGestionService offreGestionService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Recherche multicritère dans les offres publiées")
    @GetMapping("/recherche")
    public ResponseEntity<PageResponse<OffreEmploiResponse>> rechercher(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String localisation,
            @RequestParam(required = false) String typeContrat,
            @RequestParam(required = false) String niveauRequis,
            @RequestParam(required = false) String competence,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        OffreRechercheCriteres criteres = new OffreRechercheCriteres(
                q, localisation, typeContrat, niveauRequis, competence, null);
        return ResponseEntity.ok(PageResponse.from(
                offreGestionService.rechercherOffresPubliees(criteres, pageable), Function.identity()));
    }

    @Operation(summary = "Recherche dans mes offres (tous statuts)")
    @PreAuthorize("hasRole('RECRUITER')")
    @GetMapping("/mes-offres/recherche")
    public ResponseEntity<PageResponse<OffreEmploiResponse>> rechercherMesOffres(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatutOffre statut,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        OffreRechercheCriteres criteres = new OffreRechercheCriteres(
                q, null, null, null, null, statut);
        return ResponseEntity.ok(PageResponse.from(
                offreGestionService.rechercherMesOffres(criteres, currentUserService.getCurrentUser(), pageable),
                Function.identity()));
    }

    @Operation(summary = "Publier une offre (brouillon ou clôturée)")
    @PreAuthorize("hasRole('RECRUITER')")
    @PutMapping("/{id}/publier")
    public ResponseEntity<OffreEmploiResponse> publier(@PathVariable Long id) {
        return ResponseEntity.ok(offreGestionService.publier(id, currentUserService.getCurrentUser()));
    }

    @Operation(summary = "Clôturer une offre publiée")
    @PreAuthorize("hasRole('RECRUITER')")
    @PutMapping("/{id}/cloturer")
    public ResponseEntity<OffreEmploiResponse> cloturer(@PathVariable Long id) {
        return ResponseEntity.ok(offreGestionService.cloturer(id, currentUserService.getCurrentUser()));
    }

    @Operation(summary = "Archiver une offre")
    @PreAuthorize("hasRole('RECRUITER')")
    @PutMapping("/{id}/archiver")
    public ResponseEntity<OffreEmploiResponse> archiver(@PathVariable Long id) {
        return ResponseEntity.ok(offreGestionService.archiver(id, currentUserService.getCurrentUser()));
    }
}
