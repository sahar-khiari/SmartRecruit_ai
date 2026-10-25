package com.smartrecruit.dto.ai.offre;

import java.util.List;

/**
 * Compétences suggérées par l'IA (embeddings Cohere + sélection par le LLM).
 */
public record OffreIaCompetencesResponse(
        List<CompetenceSuggeree> competences,
        String methode,
        String modele,
        boolean depuisCache
) {
    public record CompetenceSuggeree(String nom, String categorie, double score, String source) {
    }
}
