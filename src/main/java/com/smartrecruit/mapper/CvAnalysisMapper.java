package com.smartrecruit.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.dto.ai.AiCvAnalysisResponse;
import com.smartrecruit.dto.ai.AiCvCoachResponse;
import com.smartrecruit.dto.response.CvAnalysisResponse;
import com.smartrecruit.dto.response.CvCoachingResponse;
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

    public String toJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Résultat d'analyse non sérialisable", ex);
        }
    }

    public Optional<AiCvAnalysisResponse> readResult(CvAnalysis analysis) {
        return read(analysis, AiCvAnalysisResponse.class);
    }

    private <T> Optional<T> read(CvAnalysis analysis, Class<T> type) {
        if (analysis.getResultJson() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(analysis.getResultJson(), type));
        } catch (JsonProcessingException ex) {
            log.warn("Résultat d'analyse illisible | analysisId={}", analysis.getId());
            return Optional.empty();
        }
    }

    public CvCoachingResponse toCoachingResponse(CvAnalysis coaching) {
        AiCvCoachResponse result = read(coaching, AiCvCoachResponse.class).orElse(null);
        return new CvCoachingResponse(
                coaching.getId(),
                coaching.getCv().getId(),
                coaching.getStatus(),
                result != null ? result.score() : null,
                result == null || result.criteria() == null ? List.of() : result.criteria().stream()
                        .map(c -> new CvCoachingResponse.Criterion(c.id(), c.label(), c.points(), c.maxPoints(),
                                c.passed(), c.advice()))
                        .toList(),
                result != null ? nullToEmpty(result.strengths()) : List.of(),
                result == null || result.improvements() == null ? List.of() : result.improvements().stream()
                        .map(i -> new CvCoachingResponse.Improvement(i.title(), i.detail())).toList(),
                result == null || result.rewrites() == null ? List.of() : result.rewrites().stream()
                        .map(r -> new CvCoachingResponse.Rewrite(r.before(), r.after(), r.reason())).toList(),
                result != null ? result.rejectedRewrites() : null,
                trace(coaching),
                coaching.getErrorCode(),
                coaching.getErrorMessage(),
                coaching.getCreatedAt()
        );
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
                trace(analysis),
                analysis.getErrorCode(),
                analysis.getErrorMessage(),
                analysis.getCreatedAt(),
                analysis.getCompletedAt(),
                analysis.getAppliedAt()
        );
    }

    private static CvAnalysisResponse.Trace trace(CvAnalysis analysis) {
        return new CvAnalysisResponse.Trace(
                analysis.getModel(),
                analysis.getFallbackUsed(),
                analysis.getPromptVersion(),
                analysis.getPipelineVersion(),
                analysis.getInputTokens(),
                analysis.getOutputTokens(),
                analysis.getDurationMs());
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
