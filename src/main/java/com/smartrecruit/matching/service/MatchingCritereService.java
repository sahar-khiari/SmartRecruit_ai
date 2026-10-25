package com.smartrecruit.matching.service;

import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.matching.dto.MatchingCritereRequest;
import com.smartrecruit.matching.dto.MatchingCritereResponse;
import com.smartrecruit.matching.engine.SkillDictionary;
import com.smartrecruit.matching.entity.MatchingCritere;
import com.smartrecruit.matching.repository.MatchingCritereRepository;
import com.smartrecruit.repository.OffreEmploiRepository;
import com.smartrecruit.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service de gestion des critères de matching par offre.
 *
 * Seul le recruteur propriétaire de l'offre (ou un admin) peut modifier les critères.
 * Auto-suggestion depuis OffreEmploi.competencesRequises via SkillDictionary.
 */
@Service
@RequiredArgsConstructor
public class MatchingCritereService {

    private final MatchingCritereRepository repo;
    private final OffreEmploiRepository      offreRepo;
    private final CurrentUserService         currentUserService;
    private final SkillDictionary            skillDict;

    /** Liste les critères d'une offre. */
    @Transactional(readOnly = true)
    public List<MatchingCritereResponse> listByOffre(Long offreId) {
        return repo.findByOffreId(offreId).stream().map(this::toResponse).toList();
    }

    /** Crée un critère. Le recruteur doit être propriétaire de l'offre. */
    @Transactional
    public MatchingCritereResponse create(MatchingCritereRequest req) {
        checkOwnership(req.offreId());
        MatchingCritere critere = MatchingCritere.builder()
                .offreId(req.offreId())
                .nom(req.nom())
                .poids(req.poids())
                .obligatoire(req.obligatoire())
                .build();
        return toResponse(repo.save(critere));
    }

    /** Met à jour un critère existant. */
    @Transactional
    public MatchingCritereResponse update(Long id, MatchingCritereRequest req) {
        MatchingCritere critere = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Critère introuvable : " + id));
        checkOwnership(critere.getOffreId());
        critere.setNom(req.nom());
        critere.setPoids(req.poids());
        critere.setObligatoire(req.obligatoire());
        return toResponse(repo.save(critere));
    }

    /** Supprime un critère. */
    @Transactional
    public void delete(Long id) {
        MatchingCritere critere = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Critère introuvable : " + id));
        checkOwnership(critere.getOffreId());
        repo.deleteById(id);
    }

    /**
     * Auto-suggère des critères à partir des compétences de l'offre.
     * Retourne des objets non persistés : le recruteur choisit ce qu'il garde.
     */
    @Transactional(readOnly = true)
    public List<MatchingCritereResponse> autoSuggest(Long offreId) {
        OffreEmploi offre = offreRepo.findById(offreId)
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + offreId));

        Set<String> skills = skillDict.extractSkills(
                (offre.getCompetencesRequises() == null ? "" : offre.getCompetencesRequises())
                + " " + offre.getDescription());

        List<MatchingCritereResponse> suggestions = new ArrayList<>();
        double defaultWeight = skills.isEmpty() ? 1.0 : Math.round(1.0 / skills.size() * 100) / 100.0;
        long rank = 0L;
        for (String skill : skills) {
            suggestions.add(new MatchingCritereResponse(
                    -(++rank), offreId, skill, defaultWeight, false));
        }
        return suggestions;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void checkOwnership(Long offreId) {
        OffreEmploi offre = offreRepo.findById(offreId)
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + offreId));

        var me = currentUserService.getCurrentUser();
        boolean isAdmin = me.getRole().getName().name().equals("ADMIN");
        boolean isOwner = offre.getRecruteur().getId().equals(me.getId());

        if (!isAdmin && !isOwner) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Vous n'êtes pas propriétaire de cette offre");
        }
    }

    private MatchingCritereResponse toResponse(MatchingCritere c) {
        return new MatchingCritereResponse(c.getId(), c.getOffreId(),
                c.getNom(), c.getPoids(), c.isObligatoire());
    }
}
