package com.smartrecruit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthResponse", description = "Réponse de connexion contenant le JWT")
public record AuthResponse(

        @Schema(description = "Jeton JWT à transmettre dans l'en-tête Authorization", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Type de jeton", example = "Bearer")
        String tokenType,

        @Schema(description = "Durée de validité du jeton en millisecondes", example = "86400000")
        long expiresIn,

        @Schema(description = "Utilisateur authentifié")
        UserResponse user
) {
}
