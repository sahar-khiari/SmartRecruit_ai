package com.smartrecruit.dto.ai.offre;

import java.util.List;

/**
 * Mots-clés détectés par l'IA, avec la méthode de référence sans IA pour comparaison.
 */
public record OffreIaMotsClesResponse(
        List<MotCle> motsCles,
        List<String> motsClesBaseline,
        String modele,
        boolean depuisCache
) {
    public record MotCle(String mot, int importance) {
    }
}
