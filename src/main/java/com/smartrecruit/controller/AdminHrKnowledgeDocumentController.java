package com.smartrecruit.controller;

import com.smartrecruit.dto.ai.AiHealthResponse;
import com.smartrecruit.dto.request.KnowledgeDocumentCreateRequest;
import com.smartrecruit.dto.request.KnowledgeDocumentStatusRequest;
import com.smartrecruit.dto.request.KnowledgeDocumentUpdateRequest;
import com.smartrecruit.dto.response.BulkKnowledgeReindexResponse;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.dto.response.KnowledgeDocumentResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.KnowledgeCategory;
import com.smartrecruit.integration.ai.AiServiceClient;
import com.smartrecruit.service.KnowledgeDocumentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration de la base de connaissances RH. Tous les endpoints sont réservés à ADMIN.
 */
@RestController
@RequestMapping(value = "/api/admin/hr/knowledge-documents", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "HR Administration",
        description = "CRUD et activation/désactivation de la base de connaissances RH. Rôle ADMIN requis.")
@SecurityRequirement(name = "bearerAuth")
public class AdminHrKnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final AiServiceClient aiServiceClient;

    @Operation(summary = "Créer une ressource de la base de connaissances",
            description = "Crée une ressource active. Le créateur est l'administrateur authentifié.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ressource créée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = KnowledgeDocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDocumentResponse create(@Valid @RequestBody KnowledgeDocumentCreateRequest request) {
        return knowledgeDocumentService.create(request);
    }

    @Operation(summary = "Lister toutes les ressources",
            description = "Liste paginée avec tri, filtre par catégorie, filtre actif/inactif "
                    + "et recherche simple dans le titre. ADMIN voit aussi les ressources désactivées.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de ressources"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<KnowledgeDocumentResponse> list(
            @Parameter(description = "Filtre par catégorie", example = "FAQ")
            @RequestParam(required = false) KnowledgeCategory category,
            @Parameter(description = "Filtre actif (true) ou inactif (false)")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Recherche simple dans le titre")
            @RequestParam(required = false) String search,
            @Parameter(description = "Pagination et tri, ex. page=0&size=20&sort=updatedAt,desc")
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return knowledgeDocumentService.searchForAdmin(category, active, search, pageable);
    }

    @Operation(summary = "Consulter une ressource par identifiant, active ou inactive")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ressource trouvée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = KnowledgeDocumentResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ressource introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public KnowledgeDocumentResponse getById(@PathVariable Long id) {
        return knowledgeDocumentService.getForAdmin(id);
    }

    @Operation(summary = "Modifier complètement une ressource")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ressource modifiée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = KnowledgeDocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ressource introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public KnowledgeDocumentResponse update(@PathVariable Long id,
                                            @Valid @RequestBody KnowledgeDocumentUpdateRequest request) {
        return knowledgeDocumentService.update(id, request);
    }

    @Operation(summary = "Activer ou désactiver une ressource",
            description = "Une ressource désactivée n'est plus retournée par l'endpoint public.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut mis à jour",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = KnowledgeDocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ressource introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public KnowledgeDocumentResponse updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody KnowledgeDocumentStatusRequest request) {
        return knowledgeDocumentService.updateStatus(id, request.active());
    }

    @Operation(summary = "Réindexer une ressource dans l'index vectoriel",
            description = "Réindexation ADMIN manuelle. Un document inactif est retiré de l'index. "
                    + "Un échec FastAPI marque le document FAILED sans le supprimer de MariaDB.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réindexation exécutée"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide"),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis"),
            @ApiResponse(responseCode = "404", description = "Ressource introuvable")
    })
    @PostMapping("/{id}/reindex")
    public KnowledgeDocumentResponse reindex(@PathVariable Long id) {
        return knowledgeDocumentService.reindex(id);
    }

    @Operation(summary = "Réindexer tous les documents actifs",
            description = "Réindexation séquentielle bornée des documents actifs. "
                    + "Les documents inactifs restent NOT_INDEXED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résultat de la réindexation globale"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide"),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis")
    })
    @PostMapping("/reindex-all")
    public BulkKnowledgeReindexResponse reindexAll() {
        return knowledgeDocumentService.reindexAll();
    }

    @Operation(summary = "État du service d'indexation vectorielle",
            description = "Diagnostic ADMIN du microservice FastAPI. Aucune clé interne n'est exposée.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service interne joignable"),
            @ApiResponse(responseCode = "503", description = "Service interne indisponible")
    })
    @GetMapping("/indexing/health")
    public AiHealthResponse indexingHealth() {
        return aiServiceClient.health();
    }

    @Operation(summary = "Supprimer définitivement une ressource",
            description = "Suppression réelle pour cette phase. La désactivation reste la méthode recommandée "
                    + "pour retirer une ressource sans en perdre le contenu.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ressource supprimée"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ressource introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        knowledgeDocumentService.delete(id);
    }
}
