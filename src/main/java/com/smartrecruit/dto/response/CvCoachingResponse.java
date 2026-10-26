package com.smartrecruit.dto.response;

import com.smartrecruit.entity.CvAnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "CvCoachingResponse",
        description = "Coach CV : score /100 calculé par règles et conseils d'amélioration rédigés par l'IA")
public record CvCoachingResponse(

        @Schema(example = "15")
        Long id,

        @Schema(example = "3")
        Long cvId,

        @Schema(example = "COMPLETED")
        CvAnalysisStatus status,

        @Schema(description = "Score /100, null si FAILED", example = "65")
        Integer score,

        List<Criterion> criteria,
        List<String> strengths,
        List<Improvement> improvements,

        @Schema(description = "Reformulations de phrases du CV (sans chiffre inventé)")
        List<Rewrite> rewrites,

        @Schema(description = "Reformulations écartées par le garde-fou anti-invention", example = "0")
        Integer rejectedRewrites,

        CvAnalysisResponse.Trace trace,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt
) {

    @Schema(name = "CvCoachingCriterion")
    public record Criterion(String id, String label, Integer points, Integer maxPoints, Boolean passed, String advice) {
    }

    @Schema(name = "CvCoachingImprovement")
    public record Improvement(String title, String detail) {
    }

    @Schema(name = "CvCoachingRewrite")
    public record Rewrite(String before, String after, String reason) {
    }
}
