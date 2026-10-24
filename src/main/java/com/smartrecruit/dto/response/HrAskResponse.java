package com.smartrecruit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "HrAskResponse",
        description = "Question utilisateur et réponse ASSISTANT fondée sur les documents RH")
public record HrAskResponse(

        HrMessageResponse userMessage,

        AssistantMessageResponse assistantMessage,

        List<MessageSourceResponse> sources,

        boolean grounded,

        boolean needsHumanReview,

        String modelName,

        String promptVersion,

        long durationMs
) {
}
