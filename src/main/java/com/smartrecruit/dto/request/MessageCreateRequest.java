package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "MessageCreateRequest",
        description = "Message utilisateur. Le rôle est imposé à USER par le serveur.")
public record MessageCreateRequest(

        @Schema(description = "Contenu du message (le rôle n'est jamais accepté depuis le client)",
                example = "Comment préparer un entretien Java ?")
        @NotBlank(message = "Le contenu du message est obligatoire")
        @Size(max = 10000, message = "Le contenu du message ne doit pas dépasser 10000 caractères")
        String content
) {
}
