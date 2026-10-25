package com.smartrecruit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MessageResponse", description = "Message informatif")
public record MessageResponse(

        @Schema(example = "Utilisateur créé avec succès")
        String message
) {
}
