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
 * Source documentaire associée à un message ASSISTANT.
 * Les métadonnées sont figées au moment de la génération pour que l'historique
 * reste lisible même si le document RH est modifié ou supprimé.
 */
@Entity
@Table(
        name = "hr_message_sources",
        indexes = {
                @Index(name = "idx_hr_message_sources_message", columnList = "message_id"),
                @Index(name = "idx_hr_message_sources_document", columnList = "document_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hr_message_sources_message"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Message message;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_id", nullable = false, length = 120)
    private String chunkId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private KnowledgeCategory category;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "document_version", nullable = false)
    private Long documentVersion;

    @Column(name = "excerpt", length = 500)
    private String excerpt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
