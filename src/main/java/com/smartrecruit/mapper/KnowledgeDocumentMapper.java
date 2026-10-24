package com.smartrecruit.mapper;

import com.smartrecruit.dto.response.KnowledgeDocumentResponse;
import com.smartrecruit.entity.KnowledgeDocument;
import com.smartrecruit.entity.User;
import org.springframework.stereotype.Component;

/**
 * Convertit un document RH en DTO. Seul l'identifiant du créateur est exposé.
 */
@Component
public class KnowledgeDocumentMapper {

    public KnowledgeDocumentResponse toResponse(KnowledgeDocument document) {
        if (document == null) {
            return null;
        }
        User createdBy = document.getCreatedBy();
        return new KnowledgeDocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getCategory(),
                document.isActive(),
                createdBy != null ? createdBy.getId() : null,
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getVersion(),
                document.getIndexingStatus(),
                document.getIndexedAt(),
                document.getIndexingError()
        );
    }
}
