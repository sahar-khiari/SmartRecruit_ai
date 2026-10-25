package com.smartrecruit.dto.request;

import com.smartrecruit.entity.KnowledgeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "RetrievalSearchRequest",
        description = "Recherche sémantique ADMIN dans l'index vectoriel, sans génération LLM")
public record RetrievalSearchRequest(

        @Schema(example = "Comment se déroule un recrutement ?")
        @NotBlank(message = "La question est obligatoire")
        @Size(max = 2000, message = "La question ne doit pas dépasser 2000 caractères")
        String query,

        @Schema(description = "Nombre maximal de chunks retournés", example = "5")
        @Min(value = 1, message = "topK doit être supérieur ou égal à 1")
        @Max(value = 10, message = "topK ne doit pas dépasser 10")
        Integer topK,

        @Schema(description = "Filtre facultatif par catégories")
        List<KnowledgeCategory> categories
) {
}
