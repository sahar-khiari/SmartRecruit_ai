package com.smartrecruit.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.dto.ai.AiCvAnalysisResponse;
import com.smartrecruit.dto.response.CvAnalysisResponse;
import com.smartrecruit.entity.CvAnalysis;
import com.smartrecruit.entity.ExperienceLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Convertit une analyse enregistrée (et son JSON) en DTO pour Angular.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CvAnalysisMapper {

    private final ObjectMapper objectMapper;

    public String toJson(AiCvAnalysisResponse result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Résultat d'analyse non sérialisable", ex);
        }
    }

    public Optional<AiCvAnalysisResponse> readResult(CvAnalysis analysis) {
        if (analysis.getResultJson() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(analysis.getResultJson(), AiCvAnalysisResponse.class));
        } catch (JsonProcessingException ex) {
            log.warn("Résultat d'analyse illisible | analysisId={}", analysis.getId());
            return Optional.empty();
        }
    }

    public CvAnalysisResponse toResponse(CvAnalysis analysis) {
        CvAnalysisResponse.Proposal proposal = readResult(analysis)
                .map(AiCvAnalysisResponse::profile)
                .map(this::toProposal)
                .orElse(null);

        return new CvAnalysisResponse(
                analysis.getId(),
                analysis.getCv().getId(),
                analysis.getStatus(),
                proposal,
                new CvAnalysisResponse.Trace(
                        analysis.getModel(),
                        analysis.getFallbackUsed(),
                        analysis.getPromptVersion(),
                        analysis.getPipelineVersion(),
                        analysis.getInputTokens(),
                        analysis.getOutputTokens(),
                        analysis.getDurationMs()),
                analysis.getErrorCode(),
                analysis.getErrorMessage(),
                analysis.getCreatedAt(),
                analysis.getCompletedAt(),
                analysis.getAppliedAt()
        );
    }

    private CvAnalysisResponse.Proposal toProposal(AiCvAnalysisResponse.Profile profile) {
        List<CvAnalysisResponse.ProposedSkill> skills = profile.skillsDetails() == null ? List.of()
                : profile.skillsDetails().stream()
                .map(skill -> new CvAnalysisResponse.ProposedSkill(
                        skill.original(), skill.name(), skill.category(), skill.method()))
                .toList();
        return new CvAnalysisResponse.Proposal(
                profile.headline(),
                profile.summary(),
                profile.yearsOfExperience(),
                // Recalculé côté Spring avec la même règle : on ne fait pas confiance à la valeur reçue.
                profile.yearsOfExperience() != null ? ExperienceLevel.fromYears(profile.yearsOfExperience()) : null,
                skills,
                nullToEmpty(profile.softSkills()),
                nullToEmpty(profile.languages())
        );
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }
}
