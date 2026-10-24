package com.smartrecruit.controller;

import com.smartrecruit.dto.request.LoginRequest;
import com.smartrecruit.dto.request.SignupRequest;
import com.smartrecruit.dto.response.AuthResponse;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.dto.response.MessageResponse;
import com.smartrecruit.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Inscription, connexion JWT et déconnexion informative")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Inscription publique",
            description = "Crée un compte avec le rôle CANDIDATE. Le rôle ne peut pas être choisi par le client. "
                    + "Le mot de passe est chiffré avec BCrypt."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Utilisateur créé avec succès",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides (validation)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Adresse email déjà utilisée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @Operation(
            summary = "Connexion",
            description = "Authentifie l'utilisateur et retourne un JWT Bearer. "
                    + "Retourne 401 si les identifiants sont incorrects et 403 si le compte est désactivé."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connexion réussie",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Email ou mot de passe incorrect",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Compte désactivé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "Déconnexion (informative)",
            description = "Aucune révocation serveur n'est implémentée : il n'existe pas de blacklist JWT. "
                    + "La déconnexion réelle consiste à supprimer le token côté client (Angular)."
    )
    @ApiResponse(responseCode = "200", description = "Message informatif",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = MessageResponse.class)))
    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse logout() {
        return authService.logout();
    }
}
