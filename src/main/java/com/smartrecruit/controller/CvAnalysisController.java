package com.smartrecruit.controller;

import com.smartrecruit.dto.request.CvAnalysisApplyRequest;
import com.smartrecruit.dto.response.CandidateProfileResponse;
import com.smartrecruit.dto.response.CvAnalysisResponse;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.service.CvAnalysisService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/candidates/me/cv", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasRole('CANDIDATE')")
@Tag(name = "Analyse IA du CV", description = "Analyse du CV par l'IA, vérification et validation par le candidat (Module 1)")
@SecurityRequirement(name = "bearerAuth")
public class CvAnalysisController {

    private final CvAnalysisService analysisService;

    @Operation(summary = "Analyser son CV courant",
            description = "Lecture du PDF, anonymisation, extraction par Gemini, normalisation des compétences. "
                    + "Environ 20 secondes. Le résultat est une proposition : le profil n'est pas modifié.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analyse terminée"),
            @ApiResponse(responseCode = "404", description = "Aucun CV déposé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Une analyse est déjà en cours",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "CV illisible, scanné ou trop long",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Service IA ou modèles indisponibles",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/analyze")
    public CvAnalysisResponse analyzeMyCv() {
        return analysisService.analyzeMyCv();
    }

    @Operation(summary = "Dernière analyse de son CV")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dernière analyse (terminée, en cours ou en échec)"),
            @ApiResponse(responseCode = "404", description = "Aucune analyse",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/analysis")
    public CvAnalysisResponse getMyLatestAnalysis() {
        return analysisService.getMyLatestAnalysis();
    }

    @Operation(summary = "Valider la proposition dans son profil",
            description = "Remplace le titre, le résumé, l'expérience et les compétences par les valeurs validées "
                    + "(éventuellement corrigées) par le candidat. Téléphone, ville et liens ne sont pas modifiés.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil mis à jour"),
            @ApiResponse(responseCode = "400", description = "Données invalides",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Analyse introuvable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Analyse non terminée",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/analysis/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CandidateProfileResponse applyMyAnalysis(@Valid @RequestBody CvAnalysisApplyRequest request) {
        return analysisService.applyMyAnalysis(request);
    }
}
