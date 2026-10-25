package com.smartrecruit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Analyse IA d'un CV (Module 1 - étape 6) : résultat et traçabilité complète
 * (modèle, secours, version du prompt et du pipeline, tokens, durée, erreur).
 * Le résultat est une proposition : rien n'est copié dans le profil tant que le candidat ne l'a pas validé.
 */
@Entity
@Table(name = "cv_analyses",
        indexes = @Index(name = "idx_cv_analyses_cv_created", columnList = "cv_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cv_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cv_analyses_cv"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CandidateCv cv;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CvAnalysisStatus status;

    /** Réponse du service IA (JSON), sans le texte du CV. */
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "model", length = 80)
    private String model;

    @Column(name = "fallback_used")
    private Boolean fallbackUsed;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(name = "pipeline_version", length = 40)
    private String pipelineVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    /** Durée totale vue par Spring (appel HTTP compris), en millisecondes. */
    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Date à laquelle le candidat a validé la proposition dans son profil. */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
