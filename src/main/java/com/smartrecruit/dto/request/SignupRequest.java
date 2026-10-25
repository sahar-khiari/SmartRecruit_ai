package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "SignupRequest", description = "Données d'inscription publique. Le rôle attribué est toujours CANDIDATE.")
public record SignupRequest(

        @Schema(description = "Prénom", example = "Sahar")
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        String firstName,

        @Schema(description = "Nom", example = "Khiari")
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String lastName,

        @Schema(description = "Adresse email unique", example = "sahar@example.com")
        @NotBlank(message = "L'adresse email est obligatoire")
        @Email(message = "L'adresse email est invalide")
        @Size(max = 180, message = "L'adresse email ne doit pas dépasser 180 caractères")
        String email,

        @Schema(description = "Mot de passe (8 caractères minimum, majuscule, minuscule, chiffre et caractère spécial)",
                example = "Password123!")
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$",
                message = "Le mot de passe doit contenir au moins une majuscule, une minuscule, un chiffre et un caractère spécial"
        )
        String password
) {
}
