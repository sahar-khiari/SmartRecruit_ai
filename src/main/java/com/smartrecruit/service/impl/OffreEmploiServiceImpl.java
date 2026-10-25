package com.smartrecruit.service.impl;

import com.smartrecruit.dto.request.OffreEmploiCreateRequest;
import com.smartrecruit.dto.response.OffreEmploiResponse;
import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.StatutOffre;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.mapper.OffreEmploiMapper;
import com.smartrecruit.repository.OffreEmploiRepository;
import com.smartrecruit.service.OffreEmploiService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation du service de gestion des offres d'emploi.
 */
@Service
@RequiredArgsConstructor
public class OffreEmploiServiceImpl implements OffreEmploiService {

    private final OffreEmploiRepository offreEmploiRepository;
    private final OffreEmploiMapper offreEmploiMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<OffreEmploiResponse> listOffresActives(Pageable pageable) {
        return offreEmploiRepository.findByActiveTrue(pageable).map(offreEmploiMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OffreEmploiResponse> listOffresRecruteur(Long recruteurId, Pageable pageable) {
        return offreEmploiRepository.findByRecruteurId(recruteurId, pageable).map(offreEmploiMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OffreEmploiResponse getById(Long id) {
        return offreEmploiMapper.toResponse(findOffreOrThrow(id));
    }

    @Override
    @Transactional
    public OffreEmploiResponse create(OffreEmploiCreateRequest request, User recruteur) {
        verifierSalaires(request);
        OffreEmploi offre = OffreEmploi.builder()
                .recruteur(recruteur)
                .build();
        appliquerChamps(offre, request);
        // null = comportement historique (publication directe).
        boolean publier = request.getPublier() == null || request.getPublier();
        offre.changerStatut(publier ? StatutOffre.PUBLIEE : StatutOffre.BROUILLON);
        return offreEmploiMapper.toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public OffreEmploiResponse update(Long id, OffreEmploiCreateRequest request, User currentUser) {
        OffreEmploi offre = findOffreOrThrow(id);
        verifierProprietaire(offre, currentUser);
        if (offre.getStatut() == StatutOffre.ARCHIVEE) {
            throw new IllegalStateException("Une offre archivée ne peut plus être modifiée");
        }
        verifierSalaires(request);
        appliquerChamps(offre, request);
        return offreEmploiMapper.toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public OffreEmploiResponse toggleStatus(Long id, boolean active, User currentUser) {
        OffreEmploi offre = findOffreOrThrow(id);
        verifierProprietaire(offre, currentUser);
        if (offre.getStatut() == StatutOffre.SUSPENDUE || offre.getStatut() == StatutOffre.ARCHIVEE) {
            throw new IllegalStateException("Le statut de cette offre ne peut pas être modifié");
        }
        offre.changerStatut(active ? StatutOffre.PUBLIEE : StatutOffre.CLOTUREE);
        return offreEmploiMapper.toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public void delete(Long id, User currentUser) {
        OffreEmploi offre = findOffreOrThrow(id);
        // Suppression : le recruteur propriétaire ou un administrateur (modération).
        if (!isAdmin(currentUser)) {
            verifierProprietaire(offre, currentUser);
        }
        offreEmploiRepository.delete(offre);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OffreEmploi findOffreOrThrow(Long id) {
        return offreEmploiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OffreEmploi", "id", id));
    }

    private void appliquerChamps(OffreEmploi offre, OffreEmploiCreateRequest request) {
        offre.setTitrePoste(request.getTitrePoste().trim());
        offre.setDescription(request.getDescription());
        offre.setNiveauRequis(request.getNiveauRequis());
        offre.setCompetencesRequises(request.getCompetencesRequises());
        offre.setLocalisation(request.getLocalisation());
        offre.setTypeContrat(request.getTypeContrat());
        offre.setDureeTestMinutes(request.getDureeTestMinutes());
        offre.setEntreprise(request.getEntreprise());
        offre.setModeTravail(request.getModeTravail());
        offre.setExperienceMinAnnees(request.getExperienceMinAnnees());
        offre.setSalaireMin(request.getSalaireMin());
        offre.setSalaireMax(request.getSalaireMax());
        offre.setDateLimite(request.getDateLimite());
        offre.setMotsCles(request.getMotsCles());
    }

    private void verifierSalaires(OffreEmploiCreateRequest request) {
        if (request.getSalaireMin() != null && request.getSalaireMax() != null
                && request.getSalaireMin() > request.getSalaireMax()) {
            throw new IllegalArgumentException("Le salaire minimum doit être inférieur ou égal au salaire maximum");
        }
    }

    /** Seul l'auteur de l'offre peut en modifier le contenu, même un administrateur ne le peut pas. */
    private void verifierProprietaire(OffreEmploi offre, User currentUser) {
        if (!offre.getRecruteur().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Vous n'êtes pas l'auteur de cette offre");
        }
    }

    private boolean isAdmin(User user) {
        return user.getRole().getName() == RoleName.ADMIN;
    }
}
