package com.smartrecruit.controller;

import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.dto.response.KnowledgeDocumentResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.KnowledgeCategory;
import com.smartrecruit.service.KnowledgeDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lecture publique authentifiée de la base de connaissances RH.
 * Seuls les documents actifs sont exposés ; aucune opération d'écriture n'existe ici.
 */
@RestController
@RequestMapping(value = "/api/hr/knowledge-documents", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'CANDIDATE')")
@Tag(name = "HR Knowledge Base",
        description = "Consultation en lecture seule des ressources RH actives. "
                + "Accessible à ADMIN, RECRUITER et CANDIDATE.")
@SecurityRequirement(name = "bearerAuth")
public class HrKnowledgeBaseController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @Operation(summary = "Lister les ressources RH actives",
            description = "Retourne uniquement les documents actifs, avec pagination, tri, "
                    + "filtre par catégorie et recherche simple dans le titre. "
                    + "Les documents désactivés ne sont jamais visibles par cet endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de ressources actives"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle non autorisé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<KnowledgeDocumentResponse> listActive(
            @Parameter(description = "Filtre par catégorie", example = "FAQ")
            @RequestParam(required = false) KnowledgeCategory category,
            @Parameter(description = "Recherche simple dans le titre")
            @RequestParam(required = false) String search,
            @Parameter(description = "Pagination et tri, ex. page=0&size=20&sort=updatedAt,desc")
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return knowledgeDocumentService.searchActive(category, search, pageable);
    }
}
