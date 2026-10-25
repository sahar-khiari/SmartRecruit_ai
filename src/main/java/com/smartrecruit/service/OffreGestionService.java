package com.smartrecruit.service;

import com.smartrecruit.dto.request.OffreRechercheCriteres;
import com.smartrecruit.dto.response.OffreEmploiResponse;
import com.smartrecruit.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Module 2 : cycle de vie et recherche multicritère des offres.
 */
public interface OffreGestionService {

    /** Recherche publique : uniquement les offres publiées et non expirées. */
    Page<OffreEmploiResponse> rechercherOffresPubliees(OffreRechercheCriteres criteres, Pageable pageable);

    /** Recherche dans les offres du recruteur connecté (tous statuts). */
    Page<OffreEmploiResponse> rechercherMesOffres(OffreRechercheCriteres criteres, User recruteur, Pageable pageable);

    /** Détail d'une offre : un candidat ne voit que les offres publiées ; l'auteur et l'admin voient tout. */
    OffreEmploiResponse consulter(Long id, User utilisateur);

    OffreEmploiResponse publier(Long id, User recruteur);

    OffreEmploiResponse cloturer(Long id, User recruteur);

    OffreEmploiResponse archiver(Long id, User recruteur);
}
