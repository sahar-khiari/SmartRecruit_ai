package com.smartrecruit.mapper;

import com.smartrecruit.dto.response.ConversationResponse;
import com.smartrecruit.entity.Conversation;
import org.springframework.stereotype.Component;

/**
 * Convertit une conversation en DTO sans exposer le propriétaire ni les entités JPA.
 */
@Component
public class ConversationMapper {

    public ConversationResponse toResponse(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}
