package com.smartrecruit.matching.dto;

import com.smartrecruit.matching.entity.StatutRecommandation;
import com.smartrecruit.matching.entity.TargetType;

import java.time.LocalDateTime;

/**
 * Recommandation de matching retournée à l'API.
 */
public record MatchingRecommandationResponse(
        Long id,
        TargetType targetType,
        Long targetId,
        Long itemId,
        String itemLabel,   // Titre de l'offre ou nom du candidat
        int rang,
        double score,
        String raison,
        StatutRecommandation statut,
        String feedback,
        LocalDateTime createdAt
) {}
