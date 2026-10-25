package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "AdminCandidateCreateRequest",
        description = "Création d'un compte CANDIDATE par un administrateur, avec un profil facultatif.")
public record AdminCandidateCreateRequest(

        @Schema(description = "Prénom", example = "Ahmed")
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        String firstName,

        @Schema(description = "Nom", example = "Ben Salah")
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String lastName,

        @Schema(description = "Adresse email unique", example = "ahmed.bensalah@example.com")
        @NotBlank(message = "L'adresse email est obligatoire")
        @Email(message = "L'adresse email est invalide")
        @Size(max = 180, message = "L'adresse email ne doit pas dépasser 180 caractères")
        String email,

        @Schema(description = "Mot de passe initial (mêmes règles que l'inscription)", example = "Password123!")
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$",
                message = "Le mot de passe doit contenir entre 8 et 100 caractères, dont une majuscule, "
                        + "une minuscule, un chiffre et un caractère spécial"
        )
        String password,

        @Schema(description = "Profil métier (facultatif)")
        @Valid
        CandidateProfileUpdateRequest profile
) {
}
