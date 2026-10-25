package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "ConversationCreateRequest",
        description = "Création d'une conversation pour l'utilisateur connecté")
public record ConversationCreateRequest(

        @Schema(description = "Titre facultatif. Valeur par défaut : Nouvelle conversation",
                example = "Préparation entretien Java", nullable = true)
        @Size(max = 200, message = "Le titre ne doit pas dépasser 200 caractères")
        String title
) {
}
