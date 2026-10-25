package com.smartrecruit.controller;

import com.smartrecruit.dto.request.UpdateUserRoleRequest;
import com.smartrecruit.dto.request.UpdateUserStatusRequest;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.dto.response.MessageResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.dto.response.UserResponse;
import com.smartrecruit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration des utilisateurs", description = "Endpoints réservés au rôle ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Lister les utilisateurs",
            description = "Liste paginée de tous les utilisateurs. Paramètres : page (0..n), size, "
                    + "sort (ex. 'id,asc', 'lastName,asc', 'email,desc').")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'utilisateurs"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<UserResponse> getAllUsers(
            @Parameter(description = "Numéro de page (0 = première page)", example = "0")
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.getAllUsers(pageable);
    }

    @Operation(summary = "Consulter un utilisateur par identifiant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilisateur trouvé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @Operation(summary = "Activer ou désactiver un compte",
            description = "Un compte désactivé ne peut plus se connecter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut mis à jour",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse updateUserStatus(@PathVariable Long id,
                                         @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.updateStatus(id, request.enabled());
    }

    @Operation(summary = "Changer le rôle d'un utilisateur",
            description = "Rôles autorisés : ADMIN, RECRUITER, CANDIDATE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rôle mis à jour",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}/role", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse updateUserRole(@PathVariable Long id,
                                       @Valid @RequestBody UpdateUserRoleRequest request) {
        return userService.updateRole(id, request.role());
    }

    @Operation(summary = "Supprimer un utilisateur",
            description = "Suppression définitive. Un administrateur ne peut pas supprimer son propre compte.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilisateur supprimé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Suppression de son propre compte refusée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public MessageResponse deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return new MessageResponse("Utilisateur supprimé avec succès");
    }
}
