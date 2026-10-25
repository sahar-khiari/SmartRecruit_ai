package com.smartrecruit.matching.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Critère de matching associé à une offre d'emploi.
 * Le recruteur peut définir plusieurs critères pondérés pour chaque offre
 * (ex. : Java → poids 0.4, Spring → poids 0.3, SQL → poids 0.3).
 * Les critères obligatoires (mandatory=true) sont des filtres durs.
 */
@Entity
@Table(
        name = "matching_criteres",
        indexes = {
                @Index(name = "idx_mc_offre_id", columnList = "offre_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchingCritere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Référence à l'offre d'emploi concernée (clé étrangère logique). */
    @Column(name = "offre_id", nullable = false)
    private Long offreId;

    /** Nom du critère (ex. : "Java", "Spring Boot", "5 ans d'expérience"). */
    @Column(name = "nom", nullable = false, length = 200)
    private String nom;

    /** Poids du critère dans le score final (0.0 – 1.0). */
    @Column(name = "poids", nullable = false)
    @Builder.Default
    private double poids = 0.25;

    /**
     * Si true, le critère est un filtre dur : un candidat qui ne le satisfait pas
     * obtient un score final de 0 et ne figure pas dans le classement.
     */
    @Column(name = "obligatoire", nullable = false)
    @Builder.Default
    private boolean obligatoire = false;
}
