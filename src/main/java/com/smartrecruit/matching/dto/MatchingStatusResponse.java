package com.smartrecruit.matching.dto;

import com.smartrecruit.matching.entity.EngineMode;

/**
 * Statut des couches de matching disponibles (renvoyé par GET /api/matching/status).
 */
public record MatchingStatusResponse(
        boolean layer0Available,    // Toujours true
        boolean layer1Available,    // FastAPI embeddings
        boolean layer2Available,    // LLM-as-judge
        EngineMode activeEngine,
        String fastApiStatus,       // "UP" | "DOWN" | "UNKNOWN"
        boolean llmEnabled,
        String llmModel,
        String llmBaseUrl
) {}
