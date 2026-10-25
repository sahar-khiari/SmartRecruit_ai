package com.smartrecruit.dto.internal;

import com.smartrecruit.entity.KnowledgeCategory;

/**
 * Instantané immuable transmis au service d'indexation hors transaction longue.
 */
public record KnowledgeDocumentIndexingSnapshot(
        Long id,
        String title,
        String content,
        KnowledgeCategory category,
        long version,
        boolean active
) {
}
