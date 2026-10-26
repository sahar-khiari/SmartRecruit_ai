package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * Réponse de POST /api/v1/cv/coach (FastAPI, JSON en snake_case) : score par règles et conseils du LLM.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiCvCoachResponse(
        String promptVersion,
        Integer score,
        List<Criterion> criteria,
        List<String> strengths,
        List<Improvement> improvements,
        List<Rewrite> rewrites,
        Integer rejectedRewrites,
        AiCvAnalysisResponse.Llm llm,
        Map<String, Integer> durationsMs
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Criterion(String id, String label, Integer points, Integer maxPoints, Boolean passed, String advice) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Improvement(String title, String detail) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rewrite(String before, String after, String reason) {
    }
}
