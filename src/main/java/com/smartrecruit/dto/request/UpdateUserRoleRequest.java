package com.smartrecruit.dto.request;

import com.smartrecruit.entity.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateUserRoleRequest", description = "Changement du rôle d'un utilisateur (ADMIN)")
public record UpdateUserRoleRequest(

        @Schema(description = "Nouveau rôle", example = "RECRUITER",
                allowableValues = {"ADMIN", "RECRUITER", "CANDIDATE"})
        @NotNull(message = "Le rôle est obligatoire")
        RoleName role
) {
}
