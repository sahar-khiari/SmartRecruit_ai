package com.smartrecruit.controller;

import com.smartrecruit.dto.ai.AiRetrievalSearchRequest;
import com.smartrecruit.dto.ai.AiRetrievalSearchResponse;
import com.smartrecruit.dto.request.RetrievalSearchRequest;
import com.smartrecruit.integration.ai.AiServiceClient;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagnostic ADMIN de la recherche sémantique.
 * Retourne des chunks, ne génère aucune réponse et n'enregistre aucun message ASSISTANT.
 */
@RestController
@RequestMapping(value = "/api/admin/hr/retrieval", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "HR Retrieval Administration",
        description = "Recherche sémantique sans LLM, réservée à ADMIN.")
@SecurityRequirement(name = "bearerAuth")
public class AdminHrRetrievalController {

    private static final int DEFAULT_TOP_K = 5;

    private final AiServiceClient aiServiceClient;

    @Operation(summary = "Rechercher des chunks RH pertinents",
            description = "Appelle FastAPI et retourne une liste de chunks classés. "
                    + "Aucun LLM, aucune réponse conversationnelle, aucun message ASSISTANT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chunks retournés",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AiRetrievalSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide"),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis"),
            @ApiResponse(responseCode = "503", description = "Service d'indexation indisponible")
    })
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AiRetrievalSearchResponse search(@Valid @RequestBody RetrievalSearchRequest request) {
        int topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        return aiServiceClient.search(new AiRetrievalSearchRequest(
                request.query().trim(),
                topK,
                request.categories(),
                null
        ));
    }
}
