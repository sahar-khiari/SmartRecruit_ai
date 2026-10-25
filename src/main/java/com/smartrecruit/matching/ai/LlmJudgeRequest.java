package com.smartrecruit.matching.ai;

import java.util.List;

/**
 * Requête envoyée à FastAPI pour le jugement LLM (Layer 2).
 * Le texte du CV est déjà anonymisé côté Spring avant envoi.
 */
public record LlmJudgeRequest(
        String cvTextAnonymise,
        String offreTexte,
        String niveauRequis,
        List<String> criteresNoms,
        String promptVersion   // "matching-v1"
) {}
