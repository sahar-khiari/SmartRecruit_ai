package com.smartrecruit.dto.ai.offre;

import java.util.List;

/**
 * Description générée par l'IA (texte complet + sections validées).
 */
public record OffreIaDescriptionResponse(
        String description,
        String accroche,
        List<String> missions,
        List<String> profil,
        List<String> avantages,
        String modele,
        String versionPrompt,
        boolean depuisCache
) {
}
