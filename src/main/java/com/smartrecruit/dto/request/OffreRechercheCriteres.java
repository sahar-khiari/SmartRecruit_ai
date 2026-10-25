package com.smartrecruit.dto.request;

import com.smartrecruit.entity.StatutOffre;

/**
 * Filtres de la recherche multicritère des offres (paramètres d'URL, tous optionnels).
 */
public record OffreRechercheCriteres(
        String q,
        String localisation,
        String typeContrat,
        String niveauRequis,
        String modeTravail,
        String competence,
        Integer salaireMin,
        StatutOffre statut,
        Long recruteurId
) {
}
