package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRetrievalSearchResponse(
        String query,
        String sanitizedQuery,
        List<AiRetrievedChunkResponse> results,
        int totalResults,
        String modelName,
        long durationMs
) {
}
