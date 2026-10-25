package com.smartrecruit.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Requête de création ou mise à jour d'une offre d'emploi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OffreEmploiCreateRequest {

    @NotBlank
    @Size(max = 200)
    private String titrePoste;

    @NotBlank
    private String description;

    @NotBlank
    @Size(max = 100)
    private String niveauRequis;

    @NotBlank
    private String competencesRequises;

    @Size(max = 200)
    private String localisation;

    @Size(max = 50)
    private String typeContrat;

    private int dureeTestMinutes = 30;

    // --- Module 2 : champs enrichis (optionnels pour rester compatible) ---

    @Size(max = 200)
    private String entreprise;

    @Pattern(regexp = "PRESENTIEL|HYBRIDE|TELETRAVAIL", message = "Mode de travail invalide")
    private String modeTravail;

    @Min(0)
    @Max(40)
    private Integer experienceMinAnnees;

    @Min(0)
    private Integer salaireMin;

    @Min(0)
    private Integer salaireMax;

    private LocalDate dateLimite;

    @Size(max = 2000)
    private String motsCles;

    /**
     * À la création : true = publier directement, false = enregistrer en brouillon.
     * Absent (null) = publication directe, comportement historique.
     */
    private Boolean publier;
}
