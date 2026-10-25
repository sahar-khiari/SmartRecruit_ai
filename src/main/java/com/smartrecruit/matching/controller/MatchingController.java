package com.smartrecruit.matching.controller;

import com.smartrecruit.matching.dto.*;
import com.smartrecruit.matching.entity.MatchingRecommandation;
import com.smartrecruit.matching.service.CandidatProfileService;
import com.smartrecruit.matching.service.MatchingCritereService;
import com.smartrecruit.matching.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Contrôleur du module Matching.
 *
 * Règles d'accès :
 *   RECRUITER → peut lancer le matching sur ses propres offres, voir les classements
 *   CANDIDATE → peut voir son profil et ses recommandations personnelles
 *   ADMIN     → accès complet
 */
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
@Tag(name = "Matching", description = "Module de matching CV ↔ offres d'emploi")
public class MatchingController {

    private final MatchingService         matchingService;
    private final CandidatProfileService  profileService;
    private final MatchingCritereService  critereService;
    private final com.smartrecruit.matching.service.SearchService searchService;

    // ─── Statut des couches ──────────────────────────────────────────────────

    @GetMapping("/status")
    @Operation(summary = "Statut des couches de matching (layer 0/1/2)")
    public ResponseEntity<MatchingStatusResponse> status() {
        return ResponseEntity.ok(matchingService.getStatus());
    }

    // ─── Profil candidat ─────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Mon profil candidat (compétences extraites, CV preview)")
    public ResponseEntity<CandidatProfileResponse> getMonProfil() {
        return ResponseEntity.ok(profileService.getMonProfil());
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Créer ou mettre à jour mon profil candidat (texte)")
    public ResponseEntity<CandidatProfileResponse> saveMonProfil(
            @Valid @RequestBody CandidatProfileRequest req) {
        return ResponseEntity.ok(profileService.saveMonProfil(req));
    }

    @PostMapping(value = "/profile/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Uploader un PDF ou fichier texte pour le profil CV")
    public ResponseEntity<CandidatProfileResponse> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "niveau", required = false) String niveau) throws IOException {
        return ResponseEntity.ok(profileService.uploadPdf(file, niveau));
    }

    @GetMapping("/profile/{userId}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    @Operation(summary = "Voir le profil d'un candidat (recruteur/admin)")
    public ResponseEntity<CandidatProfileResponse> getProfileByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(profileService.getProfileByUserId(userId));
    }

    // ─── Critères ────────────────────────────────────────────────────────────

    @GetMapping("/criteria")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    @Operation(summary = "Liste des critères d'une offre")
    public ResponseEntity<List<MatchingCritereResponse>> getCriteres(
            @RequestParam Long offreId) {
        return ResponseEntity.ok(critereService.listByOffre(offreId));
    }

    @GetMapping("/criteria/suggest")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    @Operation(summary = "Auto-suggestion de critères depuis les compétences de l'offre")
    public ResponseEntity<List<MatchingCritereResponse>> suggest(@RequestParam Long offreId) {
        return ResponseEntity.ok(critereService.autoSuggest(offreId));
    }

    @PostMapping("/criteria")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    @Operation(summary = "Créer un critère de matching pour une offre")
    public ResponseEntity<MatchingCritereResponse> createCritere(
            @Valid @RequestBody MatchingCritereRequest req) {
        return ResponseEntity.ok(critereService.create(req));
    }

    @PutMapping("/criteria/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    @Operation(summary = "Mettre à jour un critère de matching")
    public ResponseEntity<MatchingCritereResponse> updateCritere(
            @PathVariable Long id,
            @Valid @RequestBody MatchingCritereRequest req) {
        return ResponseEntity.ok(critereService.update(id, req));
    }

    @DeleteMapping("/criteria/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    @Operation(summary = "Supprimer un critère de matching")
    public ResponseEntity<Void> deleteCritere(@PathVariable Long id) {
        critereService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Matching / classement ───────────────────────────────────────────────

    @PostMapping("/offers/{offerId}/run")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    @Operation(summary = "Lancer le matching pour tous les candidats sur une offre")
    public ResponseEntity<List<MatchingScoreResponse>> runMatching(
            @PathVariable Long offerId) {
        return ResponseEntity.ok(matchingService.runMatchingForOffer(offerId));
    }

    @GetMapping("/offers/{offerId}/ranking")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    @Operation(summary = "Classement des candidats pour une offre")
    public ResponseEntity<List<MatchingScoreResponse>> getRanking(
            @PathVariable Long offerId) {
        return ResponseEntity.ok(matchingService.getRankingForOffer(offerId));
    }

    @GetMapping("/{candidateId}/{offerId}/explain")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN','CANDIDATE')")
    @Operation(summary = "Explication détaillée du score candidat ↔ offre")
    public ResponseEntity<MatchingScoreResponse> explain(
            @PathVariable Long candidateId,
            @PathVariable Long offerId) {
        return ResponseEntity.ok(matchingService.computeSingle(candidateId, offerId));
    }

    // ─── Recommandations candidat ────────────────────────────────────────────

    @GetMapping("/candidates/me/recommendations")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Mes recommandations d'offres personnalisées")
    public ResponseEntity<List<MatchingRecommandationResponse>> getMesRecommandations() {
        return ResponseEntity.ok(matchingService.getMesRecommandations());
    }

    // ─── Feedback ────────────────────────────────────────────────────────────

    @PostMapping("/feedback")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Accepter ou rejeter une recommandation (feedback)")
    public ResponseEntity<MatchingRecommandation> feedback(
            @Valid @RequestBody FeedbackRequest req) {
        return ResponseEntity.ok(matchingService.applyFeedback(
                req.recommandationId(), req.action(), req.commentaire()));
    }

    // ─── Recherche ────────────────────────────────────────────────────────────

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    @Operation(summary = "Recherche de profils par mots-clés et compétences (layer 0)")
    public ResponseEntity<List<CandidatProfileResponse>> search(
            @RequestBody SearchRequest req) {
        return ResponseEntity.ok(searchService.search(req));
    }
}
