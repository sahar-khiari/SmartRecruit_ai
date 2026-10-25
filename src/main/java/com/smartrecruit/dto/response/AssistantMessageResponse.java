package com.smartrecruit.dto.response;

import com.smartrecruit.entity.MessageRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "AssistantMessageResponse",
        description = "Message ASSISTANT avec ses sources documentaires")
public record AssistantMessageResponse(

        Long id,

        Long conversationId,

        MessageRole role,

        String content,

        LocalDateTime createdAt,

        List<MessageSourceResponse> sources
) {
}
