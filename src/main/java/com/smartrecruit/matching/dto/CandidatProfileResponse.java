package com.smartrecruit.matching.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Réponse contenant le profil candidat avec ses compétences extraites.
 */
public record CandidatProfileResponse(
        Long id,
        Long userId,
        String cvTextPreview,   // Aperçu (500 premiers caractères, jamais le texte entier)
        List<String> skills,
        String niveau,
        String resumeProfessionnel,
        LocalDateTime updatedAt
) {}
