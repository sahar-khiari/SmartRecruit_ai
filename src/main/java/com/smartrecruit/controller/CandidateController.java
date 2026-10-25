package com.smartrecruit.controller;

import com.smartrecruit.dto.request.CandidateProfileUpdateRequest;
import com.smartrecruit.dto.request.CandidateSkillsUpdateRequest;
import com.smartrecruit.dto.response.CandidateProfileResponse;
import com.smartrecruit.dto.response.CandidateSummaryResponse;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.ExperienceLevel;
import com.smartrecruit.service.CandidateService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/candidates", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Candidats", description = "Profil du candidat connecté et consultation des candidats (Module 1)")
@SecurityRequirement(name = "bearerAuth")
public class CandidateController {

    private final CandidateService candidateService;

    @Operation(summary = "Profil du candidat connecté",
            description = "Retourne le profil et les compétences. profileCompleted = false tant que "
                    + "le candidat n'a jamais enregistré son profil.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil retourné"),
            @ApiResponse(responseCode = "403", description = "Rôle CANDIDATE requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('CANDIDATE')")
    @GetMapping("/me")
    public CandidateProfileResponse getMyProfile() {
        return candidateService.getMyProfile();
    }

    @Operation(summary = "Créer ou modifier son profil",
            description = "Le profil est créé au premier enregistrement. Si experienceLevel est absent, "
                    + "il est déduit de yearsOfExperience (0-2 JUNIOR, 3-5 INTERMEDIATE, 6+ SENIOR).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil enregistré"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle CANDIDATE requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('CANDIDATE')")
    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CandidateProfileResponse updateMyProfile(@Valid @RequestBody CandidateProfileUpdateRequest request) {
        return candidateService.updateMyProfile(request);
    }

    @Operation(summary = "Remplacer ses compétences",
            description = "La liste envoyée remplace la liste actuelle. Doublons ignorés (casse ignorée).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compétences enregistrées"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle CANDIDATE requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('CANDIDATE')")
    @PutMapping(value = "/me/skills", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CandidateProfileResponse updateMySkills(@Valid @RequestBody CandidateSkillsUpdateRequest request) {
        return candidateService.updateMySkills(request);
    }

    @Operation(summary = "Rechercher des candidats",
            description = "Liste paginée des comptes CANDIDATE. search porte sur le prénom, le nom, "
                    + "le titre et les compétences ; level filtre par niveau d'expérience.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de candidats"),
            @ApiResponse(responseCode = "403", description = "Rôle RECRUITER ou ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    @GetMapping
    public PageResponse<CandidateSummaryResponse> searchCandidates(
            @Parameter(description = "Texte recherché", example = "spring")
            @RequestParam(required = false) String search,
            @Parameter(description = "Niveau d'expérience", example = "JUNIOR")
            @RequestParam(required = false) ExperienceLevel level,
            @PageableDefault(size = 10, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return candidateService.searchCandidates(search, level, pageable);
    }

    @Operation(summary = "Consulter un candidat", description = "id = identifiant du compte utilisateur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidat trouvé"),
            @ApiResponse(responseCode = "403", description = "Rôle RECRUITER ou ADMIN requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Candidat introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    @GetMapping("/{id}")
    public CandidateProfileResponse getCandidate(@PathVariable Long id) {
        return candidateService.getCandidate(id);
    }
}
