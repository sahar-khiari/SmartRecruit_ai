package com.smartrecruit.matching.dto;

import jakarta.validation.constraints.*;

/**
 * Requête de création ou mise à jour d'un critère de matching.
 */
public record MatchingCritereRequest(

        Long offreId,

        @NotBlank(message = "Le nom du critère est obligatoire")
        @Size(max = 200, message = "Le nom du critère ne doit pas dépasser 200 caractères")
        String nom,

        @DecimalMin(value = "0.0", message = "Le poids doit être entre 0 et 1")
        @DecimalMax(value = "1.0", message = "Le poids doit être entre 0 et 1")
        double poids,

        boolean obligatoire
) {}
