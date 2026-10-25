package com.smartrecruit.controller;

import com.smartrecruit.dto.request.ConversationCreateRequest;
import com.smartrecruit.dto.request.ConversationUpdateRequest;
import com.smartrecruit.dto.request.HrAskRequest;
import com.smartrecruit.dto.request.MessageCreateRequest;
import com.smartrecruit.dto.response.ConversationResponse;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.dto.response.HrAskResponse;
import com.smartrecruit.dto.response.HrMessageResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.service.ConversationService;
import com.smartrecruit.service.HrAssistantService;
import com.smartrecruit.service.MessageService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conversations privées et messages de l'Assistant RH.
 * Toutes les opérations portent sur les ressources de l'utilisateur connecté.
 */
@RestController
@RequestMapping(value = "/api/hr/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'CANDIDATE')")
@Tag(name = "HR Conversations",
        description = "Conversations et messages privés de l'utilisateur connecté "
                + "(ADMIN, RECRUITER ou CANDIDATE). Aucun userId client n'est accepté.")
@SecurityRequirement(name = "bearerAuth")
public class HrConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final HrAssistantService hrAssistantService;

    @Operation(summary = "Créer une conversation",
            description = "Crée une conversation appartenant à l'utilisateur connecté. "
                    + "Le titre est facultatif ; sans titre, « Nouvelle conversation » est utilisé. "
                    + "Le propriétaire est déterminé à partir du JWT, jamais depuis le corps de la requête.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversation créée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ConversationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@Valid @RequestBody(required = false) ConversationCreateRequest request) {
        return conversationService.create(request);
    }

    @Operation(summary = "Lister ses conversations",
            description = "Retourne uniquement les conversations de l'utilisateur connecté, avec pagination et tri.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de conversations"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<ConversationResponse> listMine(
            @Parameter(description = "Numéro de page (0 = première page)", example = "0")
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return conversationService.listMine(pageable);
    }

    @Operation(summary = "Consulter une de ses conversations",
            description = "Un 404 est retourné si la conversation n'existe pas ou n'appartient pas "
                    + "à l'utilisateur connecté, afin de ne pas révéler son existence.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation trouvée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ConversationResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation inexistante ou non accessible",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ConversationResponse getMine(@PathVariable Long id) {
        return conversationService.getMine(id);
    }

    @Operation(summary = "Renommer une de ses conversations")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation renommée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ConversationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Titre invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation inexistante ou non accessible",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ConversationResponse rename(@PathVariable Long id,
                                       @Valid @RequestBody ConversationUpdateRequest request) {
        return conversationService.rename(id, request);
    }

    @Operation(summary = "Supprimer une de ses conversations",
            description = "Supprime la conversation et tous ses messages associés.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conversation supprimée"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation inexistante ou non accessible",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        conversationService.delete(id);
    }

    @Operation(summary = "Lister les messages d'une conversation",
            description = "Retourne les messages par ordre chronologique. "
                    + "La conversation doit appartenir à l'utilisateur connecté.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Messages dans l'ordre chronologique"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation inexistante ou non accessible",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{conversationId}/messages")
    public List<HrMessageResponse> listMessages(@PathVariable Long conversationId) {
        return messageService.listForConversation(conversationId);
    }

    @Operation(summary = "Poser une question à l'Assistant RH",
            description = "Enregistre le message USER, appelle le service RAG local, "
                    + "puis enregistre le message ASSISTANT et ses sources vérifiées. "
                    + "Aucun rôle, userId, source ou modèle n'est accepté depuis le client. "
                    + "Si le contexte est insuffisant, une réponse de repli contrôlée est enregistrée.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réponse ASSISTANT générée ou repli contrôlé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HrAskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Question absente, vide ou trop longue",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle non autorisé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation inexistante ou non accessible",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Réponse IA invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Service IA ou modèle local indisponible",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "Délai de génération dépassé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/{conversationId}/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('CANDIDATE', 'RECRUITER')")
    public HrAskResponse ask(@PathVariable Long conversationId,
                             @Valid @RequestBody HrAskRequest request) {
        return hrAssistantService.ask(conversationId, request);
    }

    @Operation(summary = "Envoyer un message utilisateur sans génération",
            description = "Endpoint de compatibilité : enregistre uniquement un message USER "
                    + "avec le rôle imposé par le serveur. L'interface utilise /ask.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Message utilisateur créé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HrMessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Contenu absent, vide ou trop long",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation inexistante ou non accessible",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/{conversationId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public HrMessageResponse createMessage(@PathVariable Long conversationId,
                                         @Valid @RequestBody MessageCreateRequest request) {
        return messageService.createUserMessage(conversationId, request);
    }
}
