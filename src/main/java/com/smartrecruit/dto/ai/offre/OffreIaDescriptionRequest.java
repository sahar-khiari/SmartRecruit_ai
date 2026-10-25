package com.smartrecruit.dto.ai.offre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Informations de base fournies par le recruteur pour générer la description d'une offre.
 */
public record OffreIaDescriptionRequest(
        @NotBlank @Size(max = 200) String titrePoste,
        @Size(max = 100) String niveauRequis,
        @Size(max = 50) String typeContrat,
        @Size(max = 200) String localisation,
        @Size(max = 1000) String competences,
        @Size(max = 2000) String notes
) {
}
