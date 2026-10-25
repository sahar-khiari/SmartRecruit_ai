package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "CvAnalysisApplyRequest",
        description = "Valeurs validées (et éventuellement corrigées) par le candidat à partir de la proposition de l'IA. "
                + "Seuls le titre, le résumé, l'expérience et les compétences du profil sont remplacés.")
public record CvAnalysisApplyRequest(

        @Schema(description = "Analyse d'origine", example = "12")
        @NotNull(message = "L'identifiant de l'analyse est obligatoire")
        Long analysisId,

        @Schema(example = "Développeur Full Stack Java / Angular")
        @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères")
        String headline,

        @Size(max = 2000, message = "Le résumé ne doit pas dépasser 2000 caractères")
        String summary,

        @Schema(example = "3")
        @Min(value = 0, message = "Les années d'expérience ne peuvent pas être négatives")
        @Max(value = 60, message = "Les années d'expérience ne doivent pas dépasser 60")
        Integer yearsOfExperience,

        @Schema(description = "Compétences retenues : celles proposées par l'IA gardent l'origine AI, "
                + "celles ajoutées par le candidat deviennent MANUAL",
                example = "[\"Java\", \"Spring Boot\", \"Angular\"]")
        @NotNull(message = "La liste des compétences est obligatoire")
        @Size(max = 50, message = "50 compétences maximum")
        List<@NotBlank(message = "Une compétence ne peut pas être vide")
             @Size(max = 80, message = "Une compétence ne doit pas dépasser 80 caractères") String> skills
) {
}
