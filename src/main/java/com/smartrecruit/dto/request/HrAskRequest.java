package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Question envoyée à l'Assistant RH. Le rôle, l'utilisateur, les sources et le
 * modèle sont toujours déterminés côté serveur.
 */
@Schema(name = "HrAskRequest",
        description = "Question utilisateur. Aucun rôle, userId, source ou modèle n'est accepté.")
public record HrAskRequest(

        @Schema(description = "Question à envoyer à l'Assistant RH",
                example = "Comment postuler à une offre ?")
        @NotBlank(message = "La question est obligatoire")
        @Size(max = 2000, message = "La question ne doit pas dépasser 2000 caractères")
        String content
) {
}
