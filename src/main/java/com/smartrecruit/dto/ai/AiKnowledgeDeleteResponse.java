package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiKnowledgeDeleteResponse(
        Long documentId,
        int deletedChunks,
        String status
) {
}
