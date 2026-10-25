package com.smartrecruit.dto.response;

import com.smartrecruit.entity.IndexingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "KnowledgeDocumentReindexResponse",
        description = "Résultat d'une réindexation vectorielle ADMIN")
public record KnowledgeDocumentReindexResponse(
        Long documentId,
        IndexingStatus indexingStatus,
        LocalDateTime indexedAt,
        String indexingError
) {
}
