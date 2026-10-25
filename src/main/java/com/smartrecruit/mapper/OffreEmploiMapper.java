package com.smartrecruit.mapper;

import com.smartrecruit.dto.response.OffreEmploiResponse;
import com.smartrecruit.entity.OffreEmploi;
import org.springframework.stereotype.Component;

/**
 * Conversion entité -> DTO des offres d'emploi, partagée par tous les services d'offres.
 */
@Component
public class OffreEmploiMapper {

    public OffreEmploiResponse toResponse(OffreEmploi offre) {
        String recruteurNom = offre.getRecruteur().getFirstName() + " " + offre.getRecruteur().getLastName();
        return new OffreEmploiResponse(
                offre.getId(),
                offre.getTitrePoste(),
                offre.getDescription(),
                offre.getNiveauRequis(),
                offre.getCompetencesRequises(),
                offre.getLocalisation(),
                offre.getTypeContrat(),
                offre.getDureeTestMinutes(),
                offre.isActive(),
                offre.getRecruteur().getId(),
                recruteurNom,
                offre.getCreatedAt(),
                offre.getUpdatedAt(),
                offre.getStatut(),
                offre.getMotifSuspension(),
                offre.getEntreprise(),
                offre.getModeTravail(),
                offre.getExperienceMinAnnees(),
                offre.getSalaireMin(),
                offre.getSalaireMax(),
                offre.getDateLimite(),
                offre.getMotsCles(),
                offre.getDatePublication()
        );
    }
}
