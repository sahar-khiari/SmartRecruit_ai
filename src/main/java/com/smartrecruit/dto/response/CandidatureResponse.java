package com.smartrecruit.dto.response;

import java.time.LocalDateTime;

/**
 * Réponse DTO représentant une candidature.
 */
public record CandidatureResponse(
        Long id,
        Long offreId,
        String offreTitre,
        Long candidatId,
        String candidatNom,
        String candidatEmail,
        Integer scoreIa,
        String recommandationIa,
        String feedbackIa,
        String pointsForts,
        String pointsFaibles,
        String testId,
        String statut,
        LocalDateTime createdAt
) {
}
