package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartrecruit.entity.KnowledgeCategory;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRetrievedChunkResponse(
        Long documentId,
        String chunkId,
        int chunkIndex,
        String title,
        KnowledgeCategory category,
        String content,
        double score,
        long version
) {
}
