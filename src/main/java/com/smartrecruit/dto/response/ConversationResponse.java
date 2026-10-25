package com.smartrecruit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "ConversationResponse",
        description = "Conversation exposée à son propriétaire, sans données d'autrui")
public record ConversationResponse(

        @Schema(example = "1")
        Long id,

        @Schema(example = "Préparation entretien Java")
        String title,

        @Schema(example = "2026-10-24T10:00:00")
        LocalDateTime createdAt,

        @Schema(example = "2026-10-24T10:05:00")
        LocalDateTime updatedAt
) {
}
