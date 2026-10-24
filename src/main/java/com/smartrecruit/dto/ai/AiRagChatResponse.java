package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartrecruit.entity.KnowledgeCategory;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRagChatResponse(
        String answer,
        KnowledgeCategory category,
        boolean grounded,
        boolean needsHumanReview,
        List<AiRagSourceResponse> sources,
        String sanitizedQuestion,
        String modelName,
        String promptVersion,
        long retrievalDurationMs,
        long generationDurationMs,
        long totalDurationMs,
        Integer promptTokenCount,
        Integer completionTokenCount
) {
}
