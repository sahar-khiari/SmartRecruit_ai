package com.smartrecruit.dto.request;

import com.smartrecruit.entity.ExperienceLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CandidateProfileUpdateRequest",
        description = "Informations métier du profil candidat. Tous les champs sont facultatifs. "
                + "Si le niveau est absent, il est déduit des années d'expérience.")
public record CandidateProfileUpdateRequest(

        @Schema(description = "Titre du profil", example = "Développeur Full Stack Java / Angular")
        @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères")
        String headline,

        @Schema(description = "Téléphone", example = "+216 22 333 444")
        @Pattern(regexp = "^[+0-9 ().-]{6,30}$", message = "Le numéro de téléphone est invalide")
        String phone,

        @Schema(description = "Ville", example = "Tunis")
        @Size(max = 100, message = "La ville ne doit pas dépasser 100 caractères")
        String city,

        @Schema(description = "Lien GitHub", example = "https://github.com/jalelnasr")
        @Size(max = 255, message = "Le lien GitHub ne doit pas dépasser 255 caractères")
        @Pattern(regexp = "^https?://\\S+$", message = "Le lien GitHub doit commencer par http:// ou https://")
        String githubUrl,

        @Schema(description = "Lien LinkedIn", example = "https://www.linkedin.com/in/jalelnasr")
        @Size(max = 255, message = "Le lien LinkedIn ne doit pas dépasser 255 caractères")
        @Pattern(regexp = "^https?://\\S+$", message = "Le lien LinkedIn doit commencer par http:// ou https://")
        String linkedinUrl,

        @Schema(description = "Résumé du profil", example = "Développeur passionné par Spring Boot et Angular.")
        @Size(max = 2000, message = "Le résumé ne doit pas dépasser 2000 caractères")
        String summary,

        @Schema(description = "Niveau d'expérience (déduit des années s'il est absent)", example = "INTERMEDIATE")
        ExperienceLevel experienceLevel,

        @Schema(description = "Années d'expérience", example = "3")
        @Min(value = 0, message = "Les années d'expérience ne peuvent pas être négatives")
        @Max(value = 60, message = "Les années d'expérience ne doivent pas dépasser 60")
        Integer yearsOfExperience
) {
}
