package com.smartrecruit.controller;

import com.smartrecruit.dto.ai.offre.OffreIaCompetencesRequest;
import com.smartrecruit.dto.ai.offre.OffreIaCompetencesResponse;
import com.smartrecruit.dto.ai.offre.OffreIaDescriptionRequest;
import com.smartrecruit.dto.ai.offre.OffreIaDescriptionResponse;
import com.smartrecruit.dto.ai.offre.OffreIaMotsClesRequest;
import com.smartrecruit.dto.ai.offre.OffreIaMotsClesResponse;
import com.smartrecruit.integration.ai.OffreIaClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module 2 : assistance IA à la rédaction d'offres (Cohere via le service FastAPI).
 * Réservé aux recruteurs ; le résultat est une proposition que le recruteur relit avant d'enregistrer.
 */
@Tag(name = "Offres - assistant IA (Cohere)")
@RestController
@RequestMapping("/api/offres/ia")
@PreAuthorize("hasRole('RECRUITER')")
@RequiredArgsConstructor
public class OffreIaController {

    private final OffreIaClient offreIaClient;

    @Operation(summary = "Générer la description d'une offre")
    @PostMapping("/description")
    public ResponseEntity<OffreIaDescriptionResponse> genererDescription(
            @Valid @RequestBody OffreIaDescriptionRequest request) {
        return ResponseEntity.ok(offreIaClient.genererDescription(request));
    }

    @Operation(summary = "Suggérer les compétences requises")
    @PostMapping("/competences")
    public ResponseEntity<OffreIaCompetencesResponse> suggererCompetences(
            @Valid @RequestBody OffreIaCompetencesRequest request) {
        return ResponseEntity.ok(offreIaClient.suggererCompetences(request));
    }

    @Operation(summary = "Détecter les mots-clés importants")
    @PostMapping("/mots-cles")
    public ResponseEntity<OffreIaMotsClesResponse> detecterMotsCles(
            @Valid @RequestBody OffreIaMotsClesRequest request) {
        return ResponseEntity.ok(offreIaClient.detecterMotsCles(request));
    }
}
