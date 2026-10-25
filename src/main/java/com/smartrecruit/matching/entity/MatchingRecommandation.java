package com.smartrecruit.matching.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Recommandation de matching bi-directionnelle.
 *
 * Deux sens possibles :
 *   target_type=CANDIDATE → on recommande des offres à un candidat  (item_id = offre_id)
 *   target_type=OFFER     → on recommande des candidats à une offre  (item_id = candidate_id)
 *
 * Le statut évolue : EN_ATTENTE → ACCEPTEE | REJETEE (feedback de l'utilisateur).
 */
@Entity
@Table(
        name = "matching_recommandations",
        indexes = {
                @Index(name = "idx_mr_target",      columnList = "target_type, target_id"),
                @Index(name = "idx_mr_item",         columnList = "item_id"),
                @Index(name = "idx_mr_status",       columnList = "statut")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchingRecommandation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sens de la recommandation : CANDIDATE ou OFFER. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private TargetType targetType;

    /** ID de l'entité cible (userId si CANDIDATE, offreId si OFFER). */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** ID de l'élément recommandé (offreId si CANDIDATE, userId si OFFER). */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** Rang dans la liste de recommandations (1 = meilleur). */
    @Column(name = "rang", nullable = false)
    @Builder.Default
    private int rang = 1;

    /** Score final ayant motivé la recommandation (0–1). */
    @Column(name = "score", nullable = false)
    private double score;

    /** Explication courte pour l'utilisateur final. */
    @Column(name = "raison", columnDefinition = "TEXT")
    private String raison;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private StatutRecommandation statut = StatutRecommandation.EN_ATTENTE;

    /** Texte libre laissé par l'utilisateur lors d'un accept/reject. */
    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
