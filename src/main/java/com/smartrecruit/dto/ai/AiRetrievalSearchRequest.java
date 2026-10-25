package com.smartrecruit.dto.ai;

import com.smartrecruit.entity.KnowledgeCategory;

import java.util.List;

/**
 * Corps interne envoyé à FastAPI. Les catégories restent alignées sur l'enum Java.
 */
public record AiRetrievalSearchRequest(
        String query,
        Integer topK,
        List<KnowledgeCategory> categories,
        List<Long> documentIds
) {
}
