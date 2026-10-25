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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Candidature d'un candidat à une offre d'emploi.
 * Un candidat ne peut postuler qu'une seule fois par offre (contrainte unique).
 */
@Entity
@Table(
        name = "candidatures",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_candidatures_offre_candidat",
                columnNames = {"offre_id", "candidat_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offre_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_candidatures_offre"))
    private OffreEmploi offre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidat_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_candidatures_candidat"))
    private User candidat;

    @Column(name = "score_ia", nullable = true)
    private Integer scoreIa;

    @Column(name = "recommandation_ia", nullable = true, length = 20)
    private String recommandationIa;

    @Column(name = "feedback_ia", nullable = true, columnDefinition = "TEXT")
    private String feedbackIa;

    @Column(name = "points_forts", nullable = true, columnDefinition = "TEXT")
    private String pointsForts;

    @Column(name = "points_faibles", nullable = true, columnDefinition = "TEXT")
    private String pointsFaibles;

    @Column(name = "test_id", nullable = true, length = 100)
    private String testId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private CandidatureStatut statut = CandidatureStatut.EN_ATTENTE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
