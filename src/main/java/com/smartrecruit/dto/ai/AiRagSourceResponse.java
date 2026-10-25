package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartrecruit.entity.KnowledgeCategory;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRagSourceResponse(
        Long documentId,
        String chunkId,
        Integer chunkIndex,
        String title,
        KnowledgeCategory category,
        Double score,
        Long version,
        String excerpt
) {
}
