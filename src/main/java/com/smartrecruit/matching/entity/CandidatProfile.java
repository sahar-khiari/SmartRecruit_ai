package com.smartrecruit.matching.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Profil CV d'un candidat pour le module matching.
 * Le texte du CV est stocké en clair côté Spring ; il est anonymisé avant
 * tout envoi vers les couches IA (layer 1 et 2).
 */
@Entity
@Table(
        name = "matching_candidate_profiles",
        indexes = {
                @Index(name = "idx_mcp_user_id", columnList = "user_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CandidatProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lien vers l'utilisateur (1 profil max par candidat). */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** Texte brut du CV (collé ou extrait d'un PDF). */
    @Column(name = "cv_text", columnDefinition = "TEXT")
    private String cvText;

    /**
     * Compétences extraites au format JSON : ["java", "spring", "sql", ...].
     * Mises à jour automatiquement lors de la sauvegarde du CV.
     */
    @Column(name = "skills_json", columnDefinition = "TEXT")
    private String skillsJson;

    /**
     * Niveau d'expérience déclaré : JUNIOR | INTERMEDIAIRE | SENIOR.
     */
    @Column(name = "niveau", length = 30)
    private String niveau;

    /** Résumé professionnel (quelques lignes). */
    @Column(name = "resume_professionnel", columnDefinition = "TEXT")
    private String resumeProfessionnel;

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
