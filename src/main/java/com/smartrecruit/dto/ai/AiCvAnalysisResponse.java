package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * Réponse de POST /api/v1/cv/analyze (FastAPI, JSON en snake_case).
 * Le texte anonymisé renvoyé par FastAPI est volontairement ignoré : il n'est ni stocké ni exposé.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiCvAnalysisResponse(
        String pipelineVersion,
        Integer pageCount,
        Boolean truncated,
        Anonymization anonymization,
        Profile profile,
        Llm llm,
        Map<String, Integer> durationsMs
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Anonymization(Integer replacedCount, List<String> categories) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
            String headline,
            String summary,
            Integer yearsOfExperience,
            String experienceLevel,
            List<String> skills,
            List<SkillDetail> skillsDetails,
            String skillsReferentialVersion,
            List<String> softSkills,
            List<String> languages
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillDetail(String original, String name, String category, String method, Integer score) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Llm(
            String model,
            Boolean fallbackUsed,
            String promptVersion,
            List<Attempt> attempts,
            Integer inputTokens,
            Integer outputTokens
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attempt(String model, String errorCode, Integer latencyMs) {
    }
}
