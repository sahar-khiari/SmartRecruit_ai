package com.smartrecruit.dto.ai;

import com.smartrecruit.entity.KnowledgeCategory;

import java.util.List;

/**
 * Requête interne Spring Boot -> FastAPI. Aucun champ n'est exposé à Angular.
 */
public record AiRagChatRequest(
        String question,
        Long conversationId,
        String userRole,
        Integer topK,
        List<KnowledgeCategory> categoryHints,
        String language
) {
    public AiRagChatRequest(String question, Long conversationId, String userRole) {
        this(question, conversationId, userRole, null, null, "fr");
    }
}
