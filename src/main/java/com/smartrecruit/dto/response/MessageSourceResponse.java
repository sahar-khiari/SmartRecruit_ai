package com.smartrecruit.dto.response;

import com.smartrecruit.entity.KnowledgeCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MessageSourceResponse",
        description = "Source documentaire figée utilisée par une réponse ASSISTANT")
public record MessageSourceResponse(

        @Schema(example = "10")
        Long documentId,

        @Schema(example = "document-10-v1-chunk-0")
        String chunkId,

        @Schema(example = "0")
        Integer chunkIndex,

        @Schema(example = "Comment postuler à une offre")
        String title,

        @Schema(example = "PLATFORM_HELP")
        KnowledgeCategory category,

        @Schema(description = "Similarité vectorielle, jamais une probabilité de vérité",
                example = "0.74")
        Double score,

        @Schema(example = "1")
        Long documentVersion,

        @Schema(example = "Pour postuler, consultez les offres…")
        String excerpt
) {
}
