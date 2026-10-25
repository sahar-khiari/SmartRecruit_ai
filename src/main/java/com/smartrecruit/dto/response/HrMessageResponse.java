package com.smartrecruit.dto.response;

import com.smartrecruit.entity.MessageRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "HrMessageResponse",
        description = "Message d'une conversation avec l'Assistant RH et ses sources éventuelles")
public record HrMessageResponse(

        @Schema(example = "10")
        Long id,

        @Schema(example = "1")
        Long conversationId,

        @Schema(example = "USER")
        MessageRole role,

        @Schema(example = "Comment préparer un entretien Java ?")
        String content,

        @Schema(example = "2026-10-24T10:01:00")
        LocalDateTime createdAt,

        @Schema(description = "Sources associées à un message ASSISTANT ; vide sinon")
        List<MessageSourceResponse> sources
) {

    public HrMessageResponse(Long id, Long conversationId, MessageRole role,
                             String content, LocalDateTime createdAt) {
        this(id, conversationId, role, content, createdAt, List.of());
    }
}
