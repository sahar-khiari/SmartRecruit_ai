package com.smartrecruit.dto.response;

import com.smartrecruit.entity.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "UserResponse", description = "Utilisateur exposé par l'API (sans mot de passe)")
public record UserResponse(

        @Schema(example = "1")
        Long id,

        @Schema(example = "Sahar")
        String firstName,

        @Schema(example = "Khiari")
        String lastName,

        @Schema(example = "sahar@example.com")
        String email,

        @Schema(example = "CANDIDATE")
        RoleName role,

        @Schema(example = "true")
        boolean enabled,

        @Schema(example = "2026-10-20T14:00:00")
        LocalDateTime createdAt,

        @Schema(example = "2026-10-20T14:00:00")
        LocalDateTime updatedAt
) {
}
