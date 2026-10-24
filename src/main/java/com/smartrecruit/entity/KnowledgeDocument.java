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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Ressource de la base de connaissances RH.
 * Elle servira de corpus documentaire au futur pipeline RAG.
 */
@Entity
@Table(
        name = "hr_knowledge_documents",
        indexes = {
                @Index(name = "idx_hr_kd_active_category", columnList = "active,category"),
                @Index(name = "idx_hr_kd_title", columnList = "title")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private KnowledgeCategory category;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by", nullable = true,
            foreignKey = @ForeignKey(name = "fk_hr_kd_created_by"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Statut de synchronisation avec ChromaDB. La valeur par défaut SQL permet
     * d'ajouter la colonne sans casser une base déjà peuplée.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "indexing_status", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'NOT_INDEXED'")
    private IndexingStatus indexingStatus = IndexingStatus.NOT_INDEXED;

    @Column(name = "indexed_at", nullable = true)
    private LocalDateTime indexedAt;

    @Column(name = "indexing_error", nullable = true, length = 1000)
    private String indexingError;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.indexingStatus == null) {
            this.indexingStatus = IndexingStatus.NOT_INDEXED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
