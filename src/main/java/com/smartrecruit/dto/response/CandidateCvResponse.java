package com.smartrecruit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "CandidateCvResponse", description = "Métadonnées du CV courant d'un candidat (sans le contenu)")
public record CandidateCvResponse(

        @Schema(example = "3")
        Long id,

        @Schema(description = "Identifiant du compte du candidat", example = "5")
        Long candidateId,

        @Schema(example = "CV_Ahmed_Ben_Salah.pdf")
        String originalFilename,

        @Schema(example = "184320")
        long sizeBytes,

        @Schema(example = "2026-10-25T16:00:00")
        LocalDateTime uploadedAt
) {
}
