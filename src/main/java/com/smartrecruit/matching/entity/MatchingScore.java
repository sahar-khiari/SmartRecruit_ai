package com.smartrecruit.matching.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Score de matching entre un candidat et une offre d'emploi.
 *
 * Formule de fusion (layer 0) :
 *   final_score = w1*similarite_tfidf + w2*couverture_competences
 *               + w3*adequation_niveau + w4*score_test_technique
 * (les poids sont renormalisés si le score test est absent)
 *
 * engine_mode indique quel moteur a produit le score :
 *   JAVA       → couche 0 (TF-IDF + dict compétences)
 *   EMBEDDINGS → couche 1 (sentence-transformers via FastAPI)
 *   LLM        → couche 2 (LLM-as-judge via FastAPI)
 */
@Entity
@Table(
        name = "matching_scores",
        indexes = {
                @Index(name = "idx_ms_candidate", columnList = "candidate_id"),
                @Index(name = "idx_ms_offre",     columnList = "offre_id"),
                @Index(name = "idx_ms_pair",       columnList = "candidate_id, offre_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchingScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "offre_id", nullable = false)
    private Long offreId;

    /** Score de similarité TF-IDF ou embeddings (0–1). */
    @Column(name = "semantic_score")
    private Double semanticScore;

    /** Score donné par le LLM-judge (0–1), null si couche 2 inactive. */
    @Column(name = "llm_score")
    private Double llmScore;

    /** Score du test technique provenant de Candidature.scoreIa (0–1). */
    @Column(name = "test_score")
    private Double testScore;

    /** Adéquation niveau d'expérience (0–1). */
    @Column(name = "experience_score")
    private Double experienceScore;

    /** Score final fusionné (0–1). */
    @Column(name = "final_score", nullable = false)
    private Double finalScore;

    /**
     * Détail des composantes au format JSON.
     * Exemple : {"similarite":0.72,"competences":0.85,"niveau":0.5,"test":0.9}
     */
    @Column(name = "breakdown_json", columnDefinition = "TEXT")
    private String breakdownJson;

    /** Explication textuelle générée (template layer 0 ou LLM layer 2). */
    @Column(name = "explication", columnDefinition = "TEXT")
    private String explication;

    /** STRONG_MATCH | POSSIBLE | WEAK */
    @Column(name = "verdict", length = 20)
    private String verdict;

    /** Moteur ayant produit ce score : JAVA | EMBEDDINGS | LLM. */
    @Enumerated(EnumType.STRING)
    @Column(name = "engine_mode", nullable = false, length = 20)
    @Builder.Default
    private EngineMode engineMode = EngineMode.JAVA;

    /** Version du prompt utilisée (ex. : "matching-v1", null si layer 0). */
    @Column(name = "prompt_version", length = 60)
    private String promptVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
