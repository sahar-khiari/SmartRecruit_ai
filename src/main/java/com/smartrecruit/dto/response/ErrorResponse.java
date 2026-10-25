package com.smartrecruit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(name = "ErrorResponse", description = "Réponse d'erreur normalisée")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

        @Schema(example = "2026-10-20T14:00:00")
        LocalDateTime timestamp,

        @Schema(example = "400")
        int status,

        @Schema(example = "Bad Request")
        String error,

        @Schema(example = "Les données fournies sont invalides")
        String message,

        @Schema(example = "/api/auth/signup")
        String path,

        @Schema(description = "Erreurs de validation par champ (uniquement pour les erreurs 400 de validation)",
                example = "{\"email\": \"L'adresse email est invalide\"}")
        Map<String, String> validationErrors
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path,
                                   Map<String, String> validationErrors) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, validationErrors);
    }
}
