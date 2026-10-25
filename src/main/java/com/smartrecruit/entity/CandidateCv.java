package com.smartrecruit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Métadonnées d'un CV PDF déposé par un candidat (Module 1 - étape 2).
 * Le fichier est stocké sur disque, hors du dépôt, sous un nom aléatoire ({@code storageKey}).
 * Chaque dépôt est conservé (traçabilité des analyses IA) ; le CV courant est le plus récent.
 */
@Entity
@Table(
        name = "candidate_cvs",
        uniqueConstraints = @UniqueConstraint(name = "uk_candidate_cvs_storage_key", columnNames = "storage_key"),
        indexes = @Index(name = "idx_candidate_cvs_profile_uploaded", columnList = "profile_id, uploaded_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateCv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_candidate_cvs_profile"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CandidateProfile profile;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, length = 64)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
