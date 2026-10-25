package com.smartrecruit.matching.dto;

import java.util.List;

/**
 * Requête de recherche de profils ou d'offres par mots-clés et compétences.
 */
public record SearchRequest(
        String query,
        List<String> skills,
        String niveau,
        int page,
        int size
) {}
