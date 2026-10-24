package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Identifiants de connexion")
public record LoginRequest(

        @Schema(description = "Adresse email", example = "sahar@example.com")
        @NotBlank(message = "L'adresse email est obligatoire")
        @Email(message = "L'adresse email est invalide")
        String email,

        @Schema(description = "Mot de passe", example = "Password123!")
        @NotBlank(message = "Le mot de passe est obligatoire")
        String password
) {
}
