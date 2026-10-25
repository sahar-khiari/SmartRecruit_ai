package com.smartrecruit.dto.response;

import com.smartrecruit.entity.ExperienceLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "CandidateSummaryResponse", description = "Résumé d'un candidat pour les listes")
public record CandidateSummaryResponse(

        @Schema(description = "Identifiant du compte utilisateur", example = "5")
        Long id,

        @Schema(example = "Ahmed")
        String firstName,

        @Schema(example = "Ben Salah")
        String lastName,

        @Schema(example = "ahmed.bensalah@example.com")
        String email,

        @Schema(example = "true")
        boolean enabled,

        @Schema(example = "Développeur Full Stack Java / Angular")
        String headline,

        @Schema(example = "Tunis")
        String city,

        @Schema(example = "INTERMEDIATE")
        ExperienceLevel experienceLevel,

        @Schema(example = "3")
        Integer yearsOfExperience,

        @Schema(description = "Noms des compétences", example = "[\"Angular\", \"Java\", \"Spring Boot\"]")
        List<String> skills
) {
}
