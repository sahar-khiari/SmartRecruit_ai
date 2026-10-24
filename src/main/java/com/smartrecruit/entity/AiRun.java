package com.smartrecruit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Traçabilité technique d'une génération RAG.
 * Aucun prompt complet, secret, JWT ou donnée personnelle brute n'est stocké.
 */
@Entity
@Table(
        name = "hr_ai_runs",
        indexes = {
                @Index(name = "idx_hr_ai_runs_conversation", columnList = "conversation_id"),
                @Index(name = "idx_hr_ai_runs_user_message", columnList = "user_message_id"),
                @Index(name = "idx_hr_ai_runs_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_message_id", nullable = false)
    private Long userMessageId;

    @Column(name = "assistant_message_id")
    private Long assistantMessageId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiRunStatus status = AiRunStatus.PENDING;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "prompt_version", length = 60)
    private String promptVersion;

    @Column(name = "retrieval_duration_ms")
    private Long retrievalDurationMs;

    @Column(name = "generation_duration_ms")
    private Long generationDurationMs;

    @Column(name = "total_duration_ms")
    private Long totalDurationMs;

    @Column(name = "prompt_token_count")
    private Integer promptTokenCount;

    @Column(name = "completion_token_count")
    private Integer completionTokenCount;

    @Builder.Default
    @Column(name = "retrieved_chunk_count", nullable = false)
    private Integer retrievedChunkCount = 0;

    @Builder.Default
    @Column(name = "grounded", nullable = false)
    private Boolean grounded = false;

    @Builder.Default
    @Column(name = "needs_human_review", nullable = false)
    private Boolean needsHumanReview = false;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
