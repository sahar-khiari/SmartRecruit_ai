package com.smartrecruit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "BulkKnowledgeReindexResponse",
        description = "Résultat borné d'une réindexation globale des documents actifs")
public record BulkKnowledgeReindexResponse(
        int total,
        int indexed,
        int failed,
        List<KnowledgeDocumentReindexResponse> items
) {
}
