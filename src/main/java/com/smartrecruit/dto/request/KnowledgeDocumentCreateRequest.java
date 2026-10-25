package com.smartrecruit.dto.request;

import com.smartrecruit.entity.KnowledgeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "KnowledgeDocumentCreateRequest",
        description = "Création d'une ressource de la base de connaissances RH (ADMIN)")
public record KnowledgeDocumentCreateRequest(

        @Schema(description = "Titre de la ressource",
                example = "Comment postuler à une offre ?")
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "Le titre ne doit pas dépasser 200 caractères")
        String title,

        @Schema(description = "Contenu complet de la ressource",
                example = "Connectez-vous, ouvrez l'offre puis cliquez sur Postuler.")
        @NotBlank(message = "Le contenu est obligatoire")
        @Size(max = 100000, message = "Le contenu ne doit pas dépasser 100000 caractères")
        String content,

        @Schema(description = "Catégorie de la ressource", example = "FAQ")
        @NotNull(message = "La catégorie est obligatoire")
        KnowledgeCategory category
) {
}
