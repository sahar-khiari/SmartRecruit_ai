package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "KnowledgeDocumentStatusRequest",
        description = "Activation ou désactivation d'une ressource de la base de connaissances RH (ADMIN)")
public record KnowledgeDocumentStatusRequest(

        @Schema(description = "true pour activer, false pour désactiver", example = "false")
        @NotNull(message = "Le champ active est obligatoire")
        Boolean active
) {
}
