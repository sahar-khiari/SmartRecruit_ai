package com.smartrecruit.dto.ai.offre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Offre (titre + description) pour laquelle l'IA suggère les compétences requises.
 */
public record OffreIaCompetencesRequest(
        @NotBlank @Size(max = 200) String titrePoste,
        @Size(max = 8000) String description,
        @Size(max = 100) String niveauRequis
) {
}
