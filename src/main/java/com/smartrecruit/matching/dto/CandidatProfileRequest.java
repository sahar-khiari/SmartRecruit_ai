package com.smartrecruit.matching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Requête de création ou mise à jour du profil candidat.
 */
public record CandidatProfileRequest(

        @Size(max = 50_000, message = "Le texte du CV ne doit pas dépasser 50 000 caractères")
        String cvText,

        @Pattern(regexp = "JUNIOR|INTERMEDIAIRE|SENIOR|EXPERT",
                 message = "Niveau doit être JUNIOR, INTERMEDIAIRE, SENIOR ou EXPERT")
        String niveau,

        @Size(max = 2000, message = "Le résumé professionnel ne doit pas dépasser 2 000 caractères")
        String resumeProfessionnel
) {}
