package com.smartrecruit.matching.dto;

import com.smartrecruit.matching.entity.EngineMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Score de matching enrichi retourné à l'API.
 */
public record MatchingScoreResponse(
        Long id,
        Long candidateId,
        String candidatNom,
        String candidatEmail,
        Long offreId,
        String offreTitre,

        double finalScore,
        double semanticScore,
        Double llmScore,
        Double testScore,
        double experienceScore,

        String verdict,
        String explication,
        EngineMode engineMode,
        String promptVersion,

        List<String> matchedSkills,
        List<String> missingSkills,
        Map<String, Object> breakdown,

        LocalDateTime createdAt
) {}
