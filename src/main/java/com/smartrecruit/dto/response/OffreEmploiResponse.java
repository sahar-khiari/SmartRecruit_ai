package com.smartrecruit.dto.response;

import java.time.LocalDateTime;

/**
 * Réponse DTO représentant une offre d'emploi.
 */
public record OffreEmploiResponse(
        Long id,
        String titrePoste,
        String description,
        String niveauRequis,
        String competencesRequises,
        String localisation,
        String typeContrat,
        int dureeTestMinutes,
        boolean active,
        Long recruteurId,
        String recruteurNom,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
