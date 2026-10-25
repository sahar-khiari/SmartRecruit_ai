package com.smartrecruit.dto.response;

import com.smartrecruit.entity.IndexingStatus;
import com.smartrecruit.entity.KnowledgeCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "KnowledgeDocumentResponse",
        description = "Ressource de la base de connaissances RH, sans donnée utilisateur sensible")
public record KnowledgeDocumentResponse(

        @Schema(example = "1")
        Long id,

        @Schema(example = "Comment postuler à une offre ?")
        String title,

        @Schema(example = "Connectez-vous, ouvrez l'offre puis cliquez sur Postuler.")
        String content,

        @Schema(example = "FAQ")
        KnowledgeCategory category,

        @Schema(example = "true")
        boolean active,

        @Schema(description = "Identifiant de l'administrateur créateur (sans email ni mot de passe)",
                example = "1")
        Long createdById,

        @Schema(example = "2026-10-24T10:00:00")
        LocalDateTime createdAt,

        @Schema(example = "2026-10-24T10:05:00")
        LocalDateTime updatedAt,

        @Schema(example = "0")
        Long version,

        @Schema(description = "État de synchronisation avec l'index vectoriel",
                example = "INDEXED")
        IndexingStatus indexingStatus,

        @Schema(description = "Dernière indexation vectorielle réussie", nullable = true)
        LocalDateTime indexedAt,

        @Schema(description = "Erreur d'indexation compréhensible, sans détail interne",
                nullable = true)
        String indexingError
) {
}
