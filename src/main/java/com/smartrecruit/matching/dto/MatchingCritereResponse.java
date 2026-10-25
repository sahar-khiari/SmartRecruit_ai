package com.smartrecruit.matching.dto;

/**
 * Réponse représentant un critère de matching.
 */
public record MatchingCritereResponse(
        Long id,
        Long offreId,
        String nom,
        double poids,
        boolean obligatoire
) {}
