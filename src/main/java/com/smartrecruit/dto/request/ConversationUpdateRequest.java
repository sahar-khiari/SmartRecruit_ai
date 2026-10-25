package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ConversationUpdateRequest", description = "Renommage d'une conversation")
public record ConversationUpdateRequest(

        @Schema(description = "Nouveau titre", example = "Questions sur les entretiens")
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "Le titre ne doit pas dépasser 200 caractères")
        String title
) {
}
