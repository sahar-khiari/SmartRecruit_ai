package com.smartrecruit.matching.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.entity.Candidature;
import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.matching.ai.EmbedRankRequest;
import com.smartrecruit.matching.ai.EmbedRankResponse;
import com.smartrecruit.matching.ai.LlmJudgeRequest;
import com.smartrecruit.matching.ai.LlmJudgeResponse;
import com.smartrecruit.matching.ai.MatchingAiClient;
import com.smartrecruit.matching.dto.MatchingRecommandationResponse;
import com.smartrecruit.matching.dto.MatchingScoreResponse;
import com.smartrecruit.matching.dto.MatchingStatusResponse;
import com.smartrecruit.matching.engine.JavaMatchingEngine;
import com.smartrecruit.matching.engine.MatchingResultLayer0;
import com.smartrecruit.matching.entity.CandidatProfile;
import com.smartrecruit.matching.entity.EngineMode;
import com.smartrecruit.matching.entity.MatchingCritere;
import com.smartrecruit.matching.entity.MatchingScore;
import com.smartrecruit.matching.entity.MatchingRecommandation;
import com.smartrecruit.matching.entity.StatutRecommandation;
import com.smartrecruit.matching.entity.TargetType;
import com.smartrecruit.matching.repository.CandidatProfileRepository;
import com.smartrecruit.matching.repository.MatchingCritereRepository;
import com.smartrecruit.matching.repository.MatchingRecommandationRepository;
import com.smartrecruit.matching.repository.MatchingScoreRepository;
import com.smartrecruit.repository.CandidatureRepository;
import com.smartrecruit.repository.OffreEmploiRepository;
import com.smartrecruit.repository.UserRepository;
import com.smartrecruit.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrateur principal du module matching.
 *
 * Orchestration des 3 couches :
 *   1. Toujours : calcul Java (layer 0)
 *   2. Si FastAPI disponible : embeddings (layer 1) → améliore semantic_score
 *   3. Si LLM activé et FastAPI disponible : LLM-judge (layer 2) → enrichit le verdict
 *
 * En cas d'échec d'une couche supérieure, on reste sur la couche inférieure.
 * Les résultats sont persistés en base (matching_scores, matching_recommandations).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private final JavaMatchingEngine       javaEngine;
    private final MatchingAiClient         aiClient;
    private final CandidatProfileRepository profileRepo;
    private final MatchingCritereRepository  critereRepo;
    private final MatchingScoreRepository    scoreRepo;
    private final MatchingRecommandationRepository recRepo;
    private final OffreEmploiRepository     offreRepo;
    private final UserRepository            userRepo;
    private final CandidatureRepository     candidatureRepo;
    private final CurrentUserService        currentUserService;
    private final ObjectMapper              objectMapper;

    // ─── Calcul d'un matching individuel ─────────────────────────────────────

    /**
     * Lance le calcul de matching entre tous les candidats ayant un profil
     * et l'offre indiquée. Stocke un score par candidat.
     *
     * @param offreId ID de l'offre d'emploi
     * @return liste des scores produits
     */
    @Transactional
    public List<MatchingScoreResponse> runMatchingForOffer(Long offreId) {
        OffreEmploi offre = offreRepo.findById(offreId)
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + offreId));

        List<MatchingCritere> criteres = critereRepo.findByOffreId(offreId);
        String offreTexte = buildOffreText(offre);

        List<CandidatProfile> profiles = profileRepo.findAll();
        List<MatchingScore> scores = new ArrayList<>();

        for (CandidatProfile profile : profiles) {
            // Récupère le score test technique si le candidat a postulé
            Integer scoreTest = candidatureRepo
                    .findByOffreIdAndCandidatId(offreId, profile.getUserId())
                    .map(Candidature::getScoreIa)
                    .orElse(null);

            User candidat = userRepo.findById(profile.getUserId()).orElse(null);
            if (candidat == null) continue;

            MatchingScore score = computeAndSave(
                    profile, offre, offreTexte, criteres, scoreTest);
            scores.add(score);
        }

        // Générer les recommandations pour cette offre
        generateRecommandationsForOffer(offreId, scores);

        return scores.stream()
                .map(s -> toScoreResponse(s, offre))
                .toList();
    }

    /**
     * Calcule le matching d'un seul candidat pour une offre (pour /explain).
     */
    @Transactional
    public MatchingScoreResponse computeSingle(Long candidateId, Long offreId) {
        CandidatProfile profile = profileRepo.findByUserId(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil candidat introuvable pour l'utilisateur : " + candidateId));
        OffreEmploi offre = offreRepo.findById(offreId)
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + offreId));

        List<MatchingCritere> criteres = critereRepo.findByOffreId(offreId);
        String offreTexte = buildOffreText(offre);
        Integer scoreTest = candidatureRepo
                .findByOffreIdAndCandidatId(offreId, candidateId)
                .map(Candidature::getScoreIa)
                .orElse(null);

        MatchingScore score = computeAndSave(profile, offre, offreTexte, criteres, scoreTest);
        return toScoreResponse(score, offre);
    }

    /**
     * Retourne le classement des candidats pour une offre (meilleurs en tête).
     */
    @Transactional(readOnly = true)
    public List<MatchingScoreResponse> getRankingForOffer(Long offreId) {
        OffreEmploi offre = offreRepo.findById(offreId)
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + offreId));
        return scoreRepo.findRankingByOffreId(offreId).stream()
                .map(s -> toScoreResponse(s, offre))
                .toList();
    }

    // ─── Recommandations candidat ─────────────────────────────────────────────

    /**
     * Retourne les recommandations d'offres pour le candidat connecté.
     */
    @Transactional(readOnly = true)
    public List<MatchingRecommandationResponse> getMesRecommandations() {
        User me = currentUserService.getCurrentUser();
        List<MatchingRecommandation> list = recRepo.findByTargetTypeAndTargetIdOrderByRangAsc(
                TargetType.CANDIDATE, me.getId());

        return list.stream().map(rec -> {
            String titre = offreRepo.findById(rec.getItemId())
                    .map(OffreEmploi::getTitrePoste)
                    .orElse("Offre #" + rec.getItemId());
            return new MatchingRecommandationResponse(
                    rec.getId(),
                    rec.getTargetType(),
                    rec.getTargetId(),
                    rec.getItemId(),
                    titre,
                    rec.getRang(),
                    rec.getScore(),
                    rec.getRaison(),
                    rec.getStatut(),
                    rec.getFeedback(),
                    rec.getCreatedAt()
            );
        }).toList();
    }

    // ─── Feedback ──────────────────────────────────────────────────────────────

    @Transactional
    public MatchingRecommandation applyFeedback(Long recId, String action, String commentaire) {
        MatchingRecommandation rec = recRepo.findById(recId)
                .orElseThrow(() -> new ResourceNotFoundException("Recommandation introuvable : " + recId));

        StatutRecommandation statut = switch (action.toUpperCase()) {
            case "ACCEPTEE" -> StatutRecommandation.ACCEPTEE;
            case "REJETEE"  -> StatutRecommandation.REJETEE;
            default -> throw new IllegalArgumentException("Action invalide : " + action);
        };

        rec.setStatut(statut);
        rec.setFeedback(commentaire);
        return recRepo.save(rec);
    }

    // ─── Statut des layers ────────────────────────────────────────────────────

    public MatchingStatusResponse getStatus() {
        boolean fastApiUp = !aiClient.isCircuitOpen();
        boolean layer1 = fastApiUp;
        boolean layer2 = fastApiUp && aiClient.isLlmEnabled();
        EngineMode engine = layer2 ? EngineMode.LLM
                          : layer1 ? EngineMode.EMBEDDINGS
                          : EngineMode.JAVA;

        return new MatchingStatusResponse(
                true, layer1, layer2, engine,
                fastApiUp ? "UP" : "DOWN",
                aiClient.isLlmEnabled(),
                aiClient.getLlmModel(),
                aiClient.getLlmBaseUrl()
        );
    }

    // ─── Logique interne ──────────────────────────────────────────────────────

    private MatchingScore computeAndSave(
            CandidatProfile profile,
            OffreEmploi offre,
            String offreTexte,
            List<MatchingCritere> criteres,
            Integer scoreTestIa) {

        // Layer 0 : toujours calculé
        MatchingResultLayer0 r0 = javaEngine.compute(
                profile.getCvText(),
                offreTexte,
                profile.getNiveau(),
                offre.getNiveauRequis(),
                scoreTestIa,
                criteres);

        EngineMode engineMode = EngineMode.JAVA;
        double semanticScore  = r0.semanticScore();
        String explication    = r0.explication();
        String verdict        = r0.verdict();
        String promptVersion  = null;
        Double llmScore       = null;

        // Layer 1 : embeddings (optionnel)
        if (!aiClient.isCircuitOpen()) {
            try {
                String cvAnon = javaEngine.anonymize(profile.getCvText());
                EmbedRankRequest req = new EmbedRankRequest(offreTexte,
                        List.of(new EmbedRankRequest.CandidateTextEntry(
                                profile.getUserId(), cvAnon)));
                EmbedRankResponse resp = aiClient.embedRank(req);

                if (resp.available() && !resp.results().isEmpty()) {
                    semanticScore = resp.results().get(0).score();
                    engineMode    = EngineMode.EMBEDDINGS;
                    log.debug("[Matching] Layer 1 OK pour candidat {} offre {}",
                            profile.getUserId(), offre.getId());
                }
            } catch (Exception ex) {
                log.warn("[Matching] Layer 1 échec — fallback layer 0 : {}", ex.getMessage());
            }
        }

        // Layer 2 : LLM-judge (optionnel)
        if (aiClient.isLlmEnabled() && !aiClient.isCircuitOpen()) {
            try {
                String cvAnon = javaEngine.anonymize(profile.getCvText());
                List<String> criteresNoms = criteres.stream()
                        .map(MatchingCritere::getNom).toList();
                LlmJudgeRequest llmReq = new LlmJudgeRequest(
                        cvAnon, offreTexte, offre.getNiveauRequis(),
                        criteresNoms, "matching-v1");
                LlmJudgeResponse llmResp = aiClient.llmJudge(llmReq);

                if (llmResp.available()) {
                    llmScore     = llmResp.llmScore();
                    verdict      = llmResp.verdict() != null ? llmResp.verdict() : verdict;
                    explication  = llmResp.explication() != null ? llmResp.explication() : explication;
                    engineMode   = EngineMode.LLM;
                    promptVersion = "matching-v1";
                    log.debug("[Matching] Layer 2 OK pour candidat {} offre {}",
                            profile.getUserId(), offre.getId());
                }
            } catch (Exception ex) {
                log.warn("[Matching] Layer 2 échec — fallback layer 1/0 : {}", ex.getMessage());
            }
        }

        // Recalcul du score final avec le semantic_score potentiellement mis à jour
        double finalScore = fusionner(
                semanticScore,
                r0.skillsCoverageScore(),
                r0.experienceScore(),
                r0.testScore(),
                llmScore);

        MatchingScore entity = scoreRepo
                .findByCandidateIdAndOffreId(profile.getUserId(), offre.getId())
                .orElse(new MatchingScore());

        entity.setCandidateId(profile.getUserId());
        entity.setOffreId(offre.getId());
        entity.setSemanticScore(semanticScore);
        entity.setLlmScore(llmScore);
        entity.setTestScore(r0.testScore() >= 0 ? r0.testScore() : null);
        entity.setExperienceScore(r0.experienceScore());
        entity.setFinalScore(finalScore);
        entity.setBreakdownJson(r0.breakdownJson());
        entity.setExplication(explication);
        entity.setVerdict(verdict);
        entity.setEngineMode(engineMode);
        entity.setPromptVersion(promptVersion);

        return scoreRepo.save(entity);
    }

    /** Fusion pondérée des composantes (renormalise si test absent). */
    private double fusionner(double semantic, double skills, double niveau,
                              double test, Double llmScore) {
        double w1 = 0.35, w2 = 0.35, w3 = 0.15, w4 = test >= 0 ? 0.15 : 0.0;
        double total = w1 + w2 + w3 + w4;
        double score = (w1 * semantic + w2 * skills + w3 * niveau
                + (test >= 0 ? w4 * test : 0.0)) / total;

        // Si LLM disponible, moyenne pondérée avec le jugement LLM
        if (llmScore != null) {
            score = 0.6 * score + 0.4 * llmScore;
        }
        return Math.round(score * 1000.0) / 1000.0;
    }

    private void generateRecommandationsForOffer(Long offreId, List<MatchingScore> scores) {
        // Supprime les anciennes recommandations pour cette offre (côté recruteur)
        recRepo.deleteByTargetTypeAndTargetId(TargetType.OFFER, offreId);
        // Supprime également les anciennes recommandations de cette offre chez les candidats pour éviter les doublons
        recRepo.deleteByTargetTypeAndItemId(TargetType.CANDIDATE, offreId);

        List<MatchingScore> sorted = scores.stream()
                .filter(s -> s.getFinalScore() >= 0.1)
                .sorted((a, b) -> Double.compare(b.getFinalScore(), a.getFinalScore()))
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            MatchingScore s = sorted.get(i);
            MatchingRecommandation rec = MatchingRecommandation.builder()
                    .targetType(TargetType.OFFER)
                    .targetId(offreId)
                    .itemId(s.getCandidateId())
                    .rang(i + 1)
                    .score(s.getFinalScore())
                    .raison(buildRaisonCourte(s))
                    .build();
            recRepo.save(rec);

            // Recommandation inverse : suggère l'offre au candidat
            recRepo.save(MatchingRecommandation.builder()
                    .targetType(TargetType.CANDIDATE)
                    .targetId(s.getCandidateId())
                    .itemId(offreId)
                    .rang(i + 1)
                    .score(s.getFinalScore())
                    .raison(buildRaisonCourte(s))
                    .build());
        }
    }

    private String buildRaisonCourte(MatchingScore s) {
        return String.format("Score : %.0f%% — %s",
                s.getFinalScore() * 100,
                switch (s.getVerdict()) {
                    case "STRONG_MATCH" -> "Fort potentiel";
                    case "POSSIBLE"     -> "Profil intéressant";
                    default             -> "Inadéquat";
                });
    }

    private String buildOffreText(OffreEmploi offre) {
        return String.join(" ",
                nullSafe(offre.getTitrePoste()),
                nullSafe(offre.getDescription()),
                nullSafe(offre.getCompetencesRequises()),
                nullSafe(offre.getNiveauRequis()));
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    @SuppressWarnings("unchecked")
    private MatchingScoreResponse toScoreResponse(MatchingScore s, OffreEmploi offre) {
        User candidat = userRepo.findById(s.getCandidateId()).orElse(null);
        String nom   = candidat != null
                ? candidat.getFirstName() + " " + candidat.getLastName() : "?";
        String email = candidat != null ? candidat.getEmail() : "";

        // Parse le breakdown JSON pour l'exposer structuré
        Map<String, Object> breakdown = null;
        if (s.getBreakdownJson() != null) {
            try {
                breakdown = objectMapper.readValue(s.getBreakdownJson(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }

        // Reconstruit les listes de compétences depuis l'explication (simplification layer 0)
        // Pour un vrai retour enrichi, lire depuis la table profil + offre
        CandidatProfile profile = profileRepo.findByUserId(s.getCandidateId()).orElse(null);
        List<String> matchedSkills = List.of();
        List<String> missingSkills = List.of();
        if (profile != null) {
            OffreEmploi o = offre;
            var cvSkills = com.smartrecruit.matching.engine.SkillDictionary.class
                    .getClassLoader(); // placeholder — les skills viennent du moteur
            // Simplification : on expose les compétences du profil JSON
            try {
                if (profile.getSkillsJson() != null) {
                    matchedSkills = objectMapper.readValue(profile.getSkillsJson(),
                            new TypeReference<List<String>>() {});
                }
            } catch (Exception ignored) {}
        }

        return new MatchingScoreResponse(
                s.getId(), s.getCandidateId(), nom, email,
                s.getOffreId(), offre.getTitrePoste(),
                s.getFinalScore(),
                s.getSemanticScore() != null ? s.getSemanticScore() : 0.0,
                s.getLlmScore(),
                s.getTestScore(),
                s.getExperienceScore() != null ? s.getExperienceScore() : 0.0,
                s.getVerdict(),
                s.getExplication(),
                s.getEngineMode(),
                s.getPromptVersion(),
                matchedSkills,
                missingSkills,
                breakdown,
                s.getCreatedAt()
        );
    }
}
