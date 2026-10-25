package com.smartrecruit.service.impl;

import com.smartrecruit.dto.request.CandidatureRequest;
import com.smartrecruit.dto.response.CandidatureResponse;
import com.smartrecruit.entity.Candidature;
import com.smartrecruit.entity.CandidatureStatut;
import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.repository.CandidatureRepository;
import com.smartrecruit.repository.OffreEmploiRepository;
import com.smartrecruit.service.CandidatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implémentation du service de gestion des candidatures.
 */
@Service
@RequiredArgsConstructor
public class CandidatureServiceImpl implements CandidatureService {

    private final CandidatureRepository candidatureRepository;
    private final OffreEmploiRepository offreEmploiRepository;

    @Override
    @Transactional
    public CandidatureResponse candidater(Long offreId, CandidatureRequest request, User candidat) {
        OffreEmploi offre = offreEmploiRepository.findById(offreId)
                .orElseThrow(() -> new ResourceNotFoundException("OffreEmploi", "id", offreId));

        if (!offre.isActive()) {
            throw new IllegalStateException("Cette offre d'emploi n'est plus active");
        }

        if (candidatureRepository.existsByOffreIdAndCandidatId(offreId, candidat.getId())) {
            throw new IllegalStateException("Vous avez déjà postulé à cette offre");
        }

        Candidature candidature = Candidature.builder()
                .offre(offre)
                .candidat(candidat)
                .scoreIa(request != null ? request.getScoreIa() : null)
                .recommandationIa(request != null ? request.getRecommandationIa() : null)
                .feedbackIa(request != null ? request.getFeedbackIa() : null)
                .pointsForts(request != null ? request.getPointsForts() : null)
                .pointsFaibles(request != null ? request.getPointsFaibles() : null)
                .testId(request != null ? request.getTestId() : null)
                .statut(CandidatureStatut.EN_ATTENTE)
                .build();

        return toResponse(candidatureRepository.save(candidature));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidatureResponse> getCandidaturesParOffre(Long offreId, User currentUser) {
        OffreEmploi offre = offreEmploiRepository.findById(offreId)
                .orElseThrow(() -> new ResourceNotFoundException("OffreEmploi", "id", offreId));

        boolean isAdmin = currentUser.getRole().getName() == RoleName.ADMIN;
        boolean isRecruteur = offre.getRecruteur().getId().equals(currentUser.getId());

        if (!isAdmin && !isRecruteur) {
            throw new IllegalStateException("Accès refusé : vous n'êtes pas le recruteur de cette offre");
        }

        return candidatureRepository.findByOffreId(offreId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidatureResponse> getMesCandidatures(User candidat) {
        return candidatureRepository.findByCandidatId(candidat.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CandidatureResponse updateStatut(Long candidatureId, CandidatureStatut statut, User currentUser) {
        Candidature candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidature", "id", candidatureId));

        boolean isAdmin = currentUser.getRole().getName() == RoleName.ADMIN;
        boolean isRecruteur = candidature.getOffre().getRecruteur().getId().equals(currentUser.getId());

        if (!isAdmin && !isRecruteur) {
            throw new IllegalStateException("Accès refusé : vous n'êtes pas le recruteur de cette offre");
        }

        candidature.setStatut(statut);
        return toResponse(candidatureRepository.save(candidature));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private CandidatureResponse toResponse(Candidature c) {
        String candidatNom = c.getCandidat().getFirstName() + " " + c.getCandidat().getLastName();
        return new CandidatureResponse(
                c.getId(),
                c.getOffre().getId(),
                c.getOffre().getTitrePoste(),
                c.getCandidat().getId(),
                candidatNom,
                c.getCandidat().getEmail(),
                c.getScoreIa(),
                c.getRecommandationIa(),
                c.getFeedbackIa(),
                c.getPointsForts(),
                c.getPointsFaibles(),
                c.getTestId(),
                c.getStatut().name(),
                c.getCreatedAt()
        );
    }
}
