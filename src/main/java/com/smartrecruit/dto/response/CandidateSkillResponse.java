package com.smartrecruit.dto.response;

import com.smartrecruit.entity.SkillSource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CandidateSkillResponse", description = "Compétence d'un candidat")
public record CandidateSkillResponse(

        @Schema(example = "1")
        Long id,

        @Schema(example = "Spring Boot")
        String name,

        @Schema(description = "MANUAL = saisie par le candidat, AI = proposée par l'analyse du CV", example = "MANUAL")
        SkillSource source
) {
}
