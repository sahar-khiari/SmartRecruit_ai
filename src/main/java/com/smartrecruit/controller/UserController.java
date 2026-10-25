package com.smartrecruit.controller;

import com.smartrecruit.dto.request.ChangePasswordRequest;
import com.smartrecruit.dto.request.UpdateProfileRequest;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.dto.response.MessageResponse;
import com.smartrecruit.dto.response.UserResponse;
import com.smartrecruit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Profil utilisateur", description = "Consultation et modification du profil de l'utilisateur connecté")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Profil de l'utilisateur connecté",
            description = "Retourne les informations du compte authentifié, sans le mot de passe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil retourné",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public UserResponse getMyProfile() {
        return userService.getCurrentUserProfile();
    }

    @Operation(summary = "Modifier son profil",
            description = "Modifie uniquement firstName, lastName et email. "
                    + "Le nouvel email doit être unique. Le rôle et le statut ne sont pas modifiables ici.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil mis à jour",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email déjà utilisé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(request);
    }

    @Operation(summary = "Changer son mot de passe",
            description = "Vérifie le mot de passe actuel puis enregistre le nouveau mot de passe chiffré en BCrypt.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mot de passe modifié",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT invalide ou mot de passe actuel incorrect",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/me/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse changeMyPassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return new MessageResponse("Mot de passe modifié avec succès");
    }
}
