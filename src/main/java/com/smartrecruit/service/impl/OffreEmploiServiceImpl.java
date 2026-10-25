package com.smartrecruit.service.impl;

import com.smartrecruit.dto.request.OffreEmploiCreateRequest;
import com.smartrecruit.dto.response.OffreEmploiResponse;
import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.repository.OffreEmploiRepository;
import com.smartrecruit.service.OffreEmploiService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation du service de gestion des offres d'emploi.
 */
@Service
@RequiredArgsConstructor
public class OffreEmploiServiceImpl implements OffreEmploiService {

    private final OffreEmploiRepository offreEmploiRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<OffreEmploiResponse> listOffresActives(Pageable pageable) {
        return offreEmploiRepository.findByActiveTrue(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OffreEmploiResponse> listOffresRecruteur(Long recruteurId, Pageable pageable) {
        return offreEmploiRepository.findByRecruteurId(recruteurId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OffreEmploiResponse getById(Long id) {
        return toResponse(findOffreOrThrow(id));
    }

    @Override
    @Transactional
    public OffreEmploiResponse create(OffreEmploiCreateRequest request, User recruteur) {
        OffreEmploi offre = OffreEmploi.builder()
                .titrePoste(request.getTitrePoste().trim())
                .description(request.getDescription())
                .niveauRequis(request.getNiveauRequis())
                .competencesRequises(request.getCompetencesRequises())
                .localisation(request.getLocalisation())
                .typeContrat(request.getTypeContrat())
                .dureeTestMinutes(request.getDureeTestMinutes())
                .active(true)
                .recruteur(recruteur)
                .build();
        return toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public OffreEmploiResponse update(Long id, OffreEmploiCreateRequest request, User currentUser) {
        OffreEmploi offre = findOffreOrThrow(id);
        verifierAcces(offre, currentUser);
        offre.setTitrePoste(request.getTitrePoste().trim());
        offre.setDescription(request.getDescription());
        offre.setNiveauRequis(request.getNiveauRequis());
        offre.setCompetencesRequises(request.getCompetencesRequises());
        offre.setLocalisation(request.getLocalisation());
        offre.setTypeContrat(request.getTypeContrat());
        offre.setDureeTestMinutes(request.getDureeTestMinutes());
        return toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public OffreEmploiResponse toggleStatus(Long id, boolean active, User currentUser) {
        OffreEmploi offre = findOffreOrThrow(id);
        verifierAcces(offre, currentUser);
        offre.setActive(active);
        return toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public void delete(Long id, User currentUser) {
        OffreEmploi offre = findOffreOrThrow(id);
        verifierAcces(offre, currentUser);
        offreEmploiRepository.delete(offre);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OffreEmploi findOffreOrThrow(Long id) {
        return offreEmploiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OffreEmploi", "id", id));
    }

    private void verifierAcces(OffreEmploi offre, User currentUser) {
        boolean isAdmin = currentUser.getRole().getName() == RoleName.ADMIN;
        boolean isRecruteur = offre.getRecruteur().getId().equals(currentUser.getId());
        if (!isAdmin && !isRecruteur) {
            throw new IllegalStateException("Accès refusé : vous n'êtes pas l'auteur de cette offre");
        }
    }

    private OffreEmploiResponse toResponse(OffreEmploi offre) {
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
                offre.getUpdatedAt()
        );
    }
}
