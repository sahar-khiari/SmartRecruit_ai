package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePasswordRequest", description = "Changement du mot de passe de l'utilisateur connecté")
public record ChangePasswordRequest(

        @Schema(description = "Mot de passe actuel", example = "Password123!")
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String currentPassword,

        @Schema(description = "Nouveau mot de passe", example = "NewPassword123!")
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$",
                message = "Le mot de passe doit contenir au moins une majuscule, une minuscule, un chiffre et un caractère spécial"
        )
        String newPassword
) {
}
