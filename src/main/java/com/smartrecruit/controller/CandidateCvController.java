package com.smartrecruit.controller;

import com.smartrecruit.dto.response.CandidateCvResponse;
import com.smartrecruit.dto.response.ErrorResponse;
import com.smartrecruit.service.CandidateCvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
@Tag(name = "CV des candidats", description = "Dépôt et consultation du CV PDF (Module 1)")
@SecurityRequirement(name = "bearerAuth")
public class CandidateCvController {

    private final CandidateCvService cvService;

    @Operation(summary = "Déposer son CV (PDF, 5 Mo maximum)",
            description = "Le contenu doit être un vrai PDF (signature %PDF-). Le nouveau CV devient le CV courant ; "
                    + "les dépôts précédents sont conservés pour la traçabilité.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "CV enregistré"),
            @ApiResponse(responseCode = "400", description = "Fichier vide, absent ou qui n'est pas un PDF",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rôle CANDIDATE requis",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Fichier de plus de 5 Mo",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping(value = "/me/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateCvResponse uploadMyCv(@RequestPart("file") MultipartFile file) {
        return cvService.uploadMyCv(file);
    }

    @Operation(summary = "Métadonnées de son CV courant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CV courant"),
            @ApiResponse(responseCode = "404", description = "Aucun CV déposé",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('CANDIDATE')")
    @GetMapping(value = "/me/cv", produces = MediaType.APPLICATION_JSON_VALUE)
    public CandidateCvResponse getMyCv() {
        return cvService.getMyCv();
    }

    @Operation(summary = "Télécharger le CV courant d'un candidat",
            description = "Accessible aux rôles RECRUITER et ADMIN, et au candidat pour son propre CV.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichier PDF",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "403", description = "CV d'un autre candidat",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Candidat introuvable ou aucun CV",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/cv/file")
    public ResponseEntity<byte[]> getCvFile(@PathVariable Long id) {
        CandidateCvService.CvFile file = cvService.getCvFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.originalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(file.content());
    }
}
