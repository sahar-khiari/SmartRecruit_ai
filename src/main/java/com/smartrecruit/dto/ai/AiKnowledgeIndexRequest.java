package com.smartrecruit.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corps envoyé à FastAPI pour indexer un document issu de MariaDB.
 * Aucun userId n'est transmis : l'identité reste gérée par Spring Security.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiKnowledgeIndexRequest(
        Long documentId,
        String title,
        String content,
        String category,
        long version,
        boolean active
) {
}
