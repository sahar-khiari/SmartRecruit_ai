package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "CandidateSkillsUpdateRequest",
        description = "Liste complète des compétences : elle remplace la liste actuelle. "
                + "Les doublons (sans tenir compte de la casse) sont ignorés.")
public record CandidateSkillsUpdateRequest(

        @Schema(description = "Compétences", example = "[\"Java\", \"Spring Boot\", \"Angular\"]")
        @NotNull(message = "La liste des compétences est obligatoire")
        @Size(max = 50, message = "50 compétences maximum")
        List<@NotBlank(message = "Une compétence ne peut pas être vide")
             @Size(max = 80, message = "Une compétence ne doit pas dépasser 80 caractères") String> skills
) {
}
