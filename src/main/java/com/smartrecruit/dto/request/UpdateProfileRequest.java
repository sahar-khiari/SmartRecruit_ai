package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateProfileRequest", description = "Modification du profil de l'utilisateur connecté")
public record UpdateProfileRequest(

        @Schema(description = "Prénom", example = "Sahar")
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        String firstName,

        @Schema(description = "Nom", example = "Khiari")
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String lastName,

        @Schema(description = "Adresse email unique", example = "sahar.khiari@example.com")
        @NotBlank(message = "L'adresse email est obligatoire")
        @Email(message = "L'adresse email est invalide")
        @Size(max = 180, message = "L'adresse email ne doit pas dépasser 180 caractères")
        String email
) {
}
