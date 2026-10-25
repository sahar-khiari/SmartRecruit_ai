package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiHealthResponse(
        String status,
        String service,
        String version,
        String embeddingModel,
        boolean chromaAvailable,
        String timestamp
) {
}
