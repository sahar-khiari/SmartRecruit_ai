package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiKnowledgeIndexResponse(
        Long documentId,
        long version,
        String status,
        int chunksIndexed,
        int anonymizedItemsCount,
        String modelName,
        String indexedAt
) {
}
