package com.smartrecruit.mapper;

import com.smartrecruit.dto.response.HrMessageResponse;
import com.smartrecruit.dto.response.MessageSourceResponse;
import com.smartrecruit.entity.Message;
import com.smartrecruit.entity.MessageSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convertit un message en DTO. La conversation n'est représentée que par son identifiant.
 */
@Component
@RequiredArgsConstructor
public class MessageMapper {

    private final MessageSourceMapper messageSourceMapper;

    public HrMessageResponse toResponse(Message message) {
        return toResponse(message, List.of());
    }

    public HrMessageResponse toResponse(Message message, List<MessageSource> sources) {
        if (message == null) {
            return null;
        }
        List<MessageSourceResponse> sourceResponses = messageSourceMapper.toResponses(sources);
        return new HrMessageResponse(
                message.getId(),
                message.getConversation() != null ? message.getConversation().getId() : null,
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                sourceResponses
        );
    }
}
