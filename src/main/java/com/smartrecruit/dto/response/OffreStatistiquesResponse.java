package com.smartrecruit.dto.response;

/**
 * Compteurs affichés sur le tableau de modération des offres (admin).
 */
public record OffreStatistiquesResponse(
        long total,
        long brouillons,
        long publiees,
        long cloturees,
        long archivees,
        long suspendues
) {
}
