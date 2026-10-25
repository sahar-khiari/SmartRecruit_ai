package com.smartrecruit.dto.ai.offre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Offre dont l'IA extrait les mots-clés importants.
 */
public record OffreIaMotsClesRequest(
        @NotBlank @Size(max = 200) String titrePoste,
        @NotBlank(message = "Rédigez d'abord la description de l'offre")
        @Size(min = 20, max = 8000, message = "La description doit contenir au moins 20 caractères")
        String description
) {
}
