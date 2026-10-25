package com.smartrecruit.service;

import com.smartrecruit.dto.request.OffreEmploiCreateRequest;
import com.smartrecruit.dto.response.OffreEmploiResponse;
import com.smartrecruit.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service métier pour la gestion des offres d'emploi.
 */
public interface OffreEmploiService {

    Page<OffreEmploiResponse> listOffresActives(Pageable pageable);

    Page<OffreEmploiResponse> listOffresRecruteur(Long recruteurId, Pageable pageable);

    OffreEmploiResponse getById(Long id);

    OffreEmploiResponse create(OffreEmploiCreateRequest request, User recruteur);

    OffreEmploiResponse update(Long id, OffreEmploiCreateRequest request, User currentUser);

    OffreEmploiResponse toggleStatus(Long id, boolean active, User currentUser);

    void delete(Long id, User currentUser);
}
