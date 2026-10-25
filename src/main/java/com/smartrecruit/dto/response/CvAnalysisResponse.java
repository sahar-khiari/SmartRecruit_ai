package com.smartrecruit.dto.response;

import com.smartrecruit.entity.CvAnalysisStatus;
import com.smartrecruit.entity.ExperienceLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "CvAnalysisResponse",
        description = "Analyse IA d'un CV : proposition à vérifier par le candidat et traçabilité")
public record CvAnalysisResponse(

        @Schema(example = "12")
        Long id,

        @Schema(example = "3")
        Long cvId,

        @Schema(example = "COMPLETED")
        CvAnalysisStatus status,

        @Schema(description = "null si l'analyse a échoué")
        Proposal proposal,

        Trace trace,

        @Schema(description = "Code d'erreur si FAILED", example = "NO_TEXT")
        String errorCode,

        @Schema(description = "Message lisible si FAILED")
        String errorMessage,

        @Schema(example = "2026-10-25T18:00:00")
        LocalDateTime createdAt,

        @Schema(example = "2026-10-25T18:00:19")
        LocalDateTime completedAt,

        @Schema(description = "Date de validation par le candidat, null tant qu'il n'a pas validé")
        LocalDateTime appliedAt
) {

    @Schema(name = "CvAnalysisProposal", description = "Proposition de l'IA (l'IA propose, le candidat décide)")
    public record Proposal(
            @Schema(example = "Développeur Full Stack Java / Angular") String headline,
            String summary,
            @Schema(example = "3") Integer yearsOfExperience,
            @Schema(description = "Calculé par la règle métier", example = "INTERMEDIATE")
            ExperienceLevel experienceLevel,
            List<ProposedSkill> skills,
            List<String> softSkills,
            List<String> languages
    ) {
    }

    @Schema(name = "CvAnalysisProposedSkill")
    public record ProposedSkill(
            @Schema(description = "Telle qu'écrite dans le CV", example = "ReactJS") String original,
            @Schema(description = "Nom normalisé", example = "React") String name,
            @Schema(example = "FRAMEWORK") String category,
            @Schema(example = "EXACT") String method
    ) {
    }

    @Schema(name = "CvAnalysisTrace", description = "Traçabilité de l'analyse")
    public record Trace(
            @Schema(example = "gemini-3.5-flash") String model,
            Boolean fallbackUsed,
            @Schema(example = "cv-extract-v1") String promptVersion,
            @Schema(example = "cv-pipeline-v3") String pipelineVersion,
            Integer inputTokens,
            Integer outputTokens,
            @Schema(example = "19234") Long durationMs
    ) {
    }
}
