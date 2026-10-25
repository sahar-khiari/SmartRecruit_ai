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
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offre d'emploi publiée par un recruteur sur la plateforme SmartRecruit.
 */
@Entity
@Table(name = "offres_emploi")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreEmploi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "titre_poste", nullable = false, length = 200)
    private String titrePoste;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "niveau_requis", nullable = false, length = 100)
    private String niveauRequis;

    @Column(name = "competences_requises", columnDefinition = "TEXT")
    private String competencesRequises;

    @Column(name = "localisation", length = 200)
    private String localisation;

    @Column(name = "type_contrat", length = 50)
    private String typeContrat;

    @Builder.Default
    @Column(name = "duree_test_minutes", nullable = false)
    private int dureeTestMinutes = 30;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // --- Module 2 : cycle de vie, modération et champs enrichis ---

    /** Statut détaillé de l'offre ; {@code active} reste synchronisé (true uniquement si PUBLIEE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", length = 20)
    private StatutOffre statut;

    @Column(name = "entreprise", length = 200)
    private String entreprise;

    /** PRESENTIEL, HYBRIDE ou TELETRAVAIL. */
    @Column(name = "mode_travail", length = 30)
    private String modeTravail;

    @Column(name = "experience_min_annees")
    private Integer experienceMinAnnees;

    @Column(name = "salaire_min")
    private Integer salaireMin;

    @Column(name = "salaire_max")
    private Integer salaireMax;

    @Column(name = "date_limite")
    private LocalDate dateLimite;

    /** Mots-clés séparés par des virgules (saisis ou détectés par l'IA). */
    @Column(name = "mots_cles", columnDefinition = "TEXT")
    private String motsCles;

    @Column(name = "date_publication")
    private LocalDateTime datePublication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recruteur_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_offres_recruteur"))
    private User recruteur;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Change le statut et garde le champ {@code active} cohérent pour le flux de candidature. */
    public void changerStatut(StatutOffre nouveauStatut) {
        this.statut = nouveauStatut;
        this.active = nouveauStatut == StatutOffre.PUBLIEE;
        if (nouveauStatut == StatutOffre.PUBLIEE && this.datePublication == null) {
            this.datePublication = LocalDateTime.now();
        }
    }

    /** Les offres créées avant le Module 2 n'ont pas de statut : on le déduit de {@code active}. */
    @PostLoad
    protected void onLoad() {
        if (this.statut == null) {
            this.statut = this.active ? StatutOffre.PUBLIEE : StatutOffre.CLOTUREE;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.statut == null) {
            changerStatut(this.active ? StatutOffre.PUBLIEE : StatutOffre.BROUILLON);
        }
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
