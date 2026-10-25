package com.smartrecruit.matching.ai;

import java.util.List;
import java.util.Map;

/**
 * Requête envoyée à FastAPI pour le classement par embeddings (Layer 1).
 */
public record EmbedRankRequest(
        String offreTexte,
        List<CandidateTextEntry> candidates
) {
    /** Texte anonymisé du CV associé à l'identifiant du candidat. */
    public record CandidateTextEntry(Long candidateId, String cvText) {}
}
