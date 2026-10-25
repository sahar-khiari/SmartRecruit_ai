package com.smartrecruit.matching.ai;

import java.util.List;
import java.util.Map;

/**
 * Réponse JSON structurée du LLM-judge (Layer 2).
 * Validée par Pydantic côté FastAPI, puis ici par Jackson.
 */
public record LlmJudgeResponse(
        boolean available,

        /** Scores par critère : {nomCritère: score 0-100}. */
        Map<String, Integer> criteriaScores,

        /** Compétences détectées dans le CV (verbatim, anonymisé). */
        List<String> evidenceQuotes,

        /** Compétences requises absentes du CV. */
        List<String> missingSkills,

        /** JUNIOR | INTERMEDIAIRE | SENIOR | EXPERT */
        String seniorityFit,

        /** STRONG_MATCH | POSSIBLE | WEAK */
        String verdict,

        /** Score global normalisé (0–1). */
        double llmScore,

        /** Explication en français (≤ 300 mots). */
        String explication,

        String errorMessage
) {}
