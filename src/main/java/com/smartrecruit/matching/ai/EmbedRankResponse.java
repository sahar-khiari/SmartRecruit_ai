package com.smartrecruit.matching.ai;

import java.util.List;

/**
 * Résultat retourné par FastAPI /api/v1/matching/embed-rank (Layer 1).
 */
public record EmbedRankResponse(
        boolean available,
        List<EmbedRankResult> results,
        String errorMessage
) {
    public record EmbedRankResult(
            Long candidateId,
            double score,
            String engine
    ) {}
}
