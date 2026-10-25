package com.smartrecruit.dto.response;

import com.smartrecruit.entity.ExperienceLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "CandidateProfileResponse",
        description = "Profil complet d'un candidat. L'identifiant est celui de son compte utilisateur.")
public record CandidateProfileResponse(

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

        @Schema(description = "false tant que le profil n'a jamais été enregistré", example = "true")
        boolean profileCompleted,

        @Schema(example = "Développeur Full Stack Java / Angular")
        String headline,

        @Schema(example = "+216 22 333 444")
        String phone,

        @Schema(example = "Tunis")
        String city,

        @Schema(example = "https://github.com/ahmed")
        String githubUrl,

        @Schema(example = "https://www.linkedin.com/in/ahmed")
        String linkedinUrl,

        @Schema(example = "Développeur passionné par Spring Boot et Angular.")
        String summary,

        @Schema(example = "INTERMEDIATE")
        ExperienceLevel experienceLevel,

        @Schema(example = "3")
        Integer yearsOfExperience,

        @Schema(description = "Compétences, triées par nom")
        List<CandidateSkillResponse> skills,

        @Schema(example = "2026-10-20T14:00:00")
        LocalDateTime createdAt,

        @Schema(example = "2026-10-20T14:00:00")
        LocalDateTime updatedAt
) {
}
