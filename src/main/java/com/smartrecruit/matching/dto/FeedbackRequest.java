package com.smartrecruit.matching.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Requête de feedback (accepter ou rejeter une recommandation).
 */
public record FeedbackRequest(

        @NotNull(message = "L'identifiant de la recommandation est obligatoire")
        Long recommandationId,

        @NotNull(message = "L'action est obligatoire : ACCEPTEE ou REJETEE")
        String action,   // ACCEPTEE | REJETEE

        @Size(max = 1000)
        String commentaire
) {}
