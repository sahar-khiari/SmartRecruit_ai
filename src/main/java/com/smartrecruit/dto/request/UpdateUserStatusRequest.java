package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateUserStatusRequest", description = "Activation ou désactivation d'un compte (ADMIN)")
public record UpdateUserStatusRequest(

        @Schema(description = "true pour activer le compte, false pour le désactiver", example = "false")
        @NotNull(message = "Le champ enabled est obligatoire")
        Boolean enabled
) {
}
