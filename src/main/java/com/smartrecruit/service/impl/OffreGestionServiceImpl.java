package com.smartrecruit.service.impl;

import com.smartrecruit.dto.request.OffreRechercheCriteres;
import com.smartrecruit.dto.response.OffreEmploiResponse;
import com.smartrecruit.dto.response.OffreStatistiquesResponse;
import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.StatutOffre;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.mapper.OffreEmploiMapper;
import com.smartrecruit.repository.OffreEmploiRepository;
import com.smartrecruit.service.OffreGestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static com.smartrecruit.repository.OffreEmploiSpecifications.champEgal;
import static com.smartrecruit.repository.OffreEmploiSpecifications.competence;
import static com.smartrecruit.repository.OffreEmploiSpecifications.localisation;
import static com.smartrecruit.repository.OffreEmploiSpecifications.motCle;
import static com.smartrecruit.repository.OffreEmploiSpecifications.recruteur;
import static com.smartrecruit.repository.OffreEmploiSpecifications.salaireMin;
import static com.smartrecruit.repository.OffreEmploiSpecifications.statut;
import static com.smartrecruit.repository.OffreEmploiSpecifications.visiblesCandidats;

/**
 * Règles du cycle de vie :
 * BROUILLON/CLOTUREE -> PUBLIEE (recruteur), PUBLIEE -> CLOTUREE (recruteur),
 * tout sauf SUSPENDUE -> ARCHIVEE (recruteur), tout sauf ARCHIVEE -> SUSPENDUE (admin),
 * SUSPENDUE -> PUBLIEE (admin).
 */
@Service
@RequiredArgsConstructor
public class OffreGestionServiceImpl implements OffreGestionService {

    private final OffreEmploiRepository offreEmploiRepository;
    private final OffreEmploiMapper offreEmploiMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<OffreEmploiResponse> rechercherOffresPubliees(OffreRechercheCriteres criteres, Pageable pageable) {
        Specification<OffreEmploi> spec = Specification.where(visiblesCandidats()).and(filtresCommuns(criteres));
        return offreEmploiRepository.findAll(spec, pageable).map(offreEmploiMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OffreEmploiResponse> rechercherMesOffres(OffreRechercheCriteres criteres, User recruteurConnecte,
                                                         Pageable pageable) {
        Specification<OffreEmploi> spec = Specification.where(recruteur(recruteurConnecte.getId()))
                .and(statut(criteres.statut()))
                .and(filtresCommuns(criteres));
        return offreEmploiRepository.findAll(spec, pageable).map(offreEmploiMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OffreEmploiResponse> rechercherToutesOffres(OffreRechercheCriteres criteres, Pageable pageable) {
        Specification<OffreEmploi> spec = Specification.where(statut(criteres.statut()))
                .and(recruteur(criteres.recruteurId()))
                .and(filtresCommuns(criteres));
        return offreEmploiRepository.findAll(spec, pageable).map(offreEmploiMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OffreEmploiResponse consulter(Long id, User utilisateur) {
        OffreEmploi offre = findOrThrow(id);
        boolean auteur = offre.getRecruteur().getId().equals(utilisateur.getId());
        boolean admin = utilisateur.getRole().getName() == RoleName.ADMIN;
        if (offre.getStatut() != StatutOffre.PUBLIEE && !auteur && !admin) {
            // 404 plutôt que 403 : on ne révèle pas l'existence d'un brouillon.
            throw new ResourceNotFoundException("OffreEmploi", "id", id);
        }
        return offreEmploiMapper.toResponse(offre);
    }

    @Override
    @Transactional
    public OffreEmploiResponse publier(Long id, User recruteurConnecte) {
        OffreEmploi offre = findProprietaire(id, recruteurConnecte);
        verifierStatut(offre, EnumSet.of(StatutOffre.BROUILLON, StatutOffre.CLOTUREE), "publiée");
        if (offre.getDateLimite() != null && offre.getDateLimite().isBefore(LocalDate.now())) {
            throw new IllegalStateException("La date limite est dépassée : modifiez-la avant de publier");
        }
        offre.changerStatut(StatutOffre.PUBLIEE);
        return offreEmploiMapper.toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public OffreEmploiResponse cloturer(Long id, User recruteurConnecte) {
        OffreEmploi offre = findProprietaire(id, recruteurConnecte);
        verifierStatut(offre, EnumSet.of(StatutOffre.PUBLIEE), "clôturée");
        offre.changerStatut(StatutOffre.CLOTUREE);
        return offreEmploiMapper.toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public OffreEmploiResponse archiver(Long id, User recruteurConnecte) {
        OffreEmploi offre = findProprietaire(id, recruteurConnecte);
        verifierStatut(offre, EnumSet.of(StatutOffre.BROUILLON, StatutOffre.PUBLIEE, StatutOffre.CLOTUREE),
                "archivée");
        offre.changerStatut(StatutOffre.ARCHIVEE);
        return offreEmploiMapper.toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public OffreEmploiResponse suspendre(Long id, String motif) {
        OffreEmploi offre = findOrThrow(id);
        verifierStatut(offre, EnumSet.of(StatutOffre.BROUILLON, StatutOffre.PUBLIEE, StatutOffre.CLOTUREE),
                "suspendue");
        offre.changerStatut(StatutOffre.SUSPENDUE);
        offre.setMotifSuspension(motif.trim());
        return offreEmploiMapper.toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional
    public OffreEmploiResponse reactiver(Long id) {
        OffreEmploi offre = findOrThrow(id);
        verifierStatut(offre, EnumSet.of(StatutOffre.SUSPENDUE), "réactivée");
        offre.changerStatut(StatutOffre.PUBLIEE);
        offre.setMotifSuspension(null);
        return offreEmploiMapper.toResponse(offreEmploiRepository.save(offre));
    }

    @Override
    @Transactional(readOnly = true)
    public OffreStatistiquesResponse statistiques() {
        return new OffreStatistiquesResponse(
                offreEmploiRepository.count(),
                offreEmploiRepository.countByStatut(StatutOffre.BROUILLON),
                offreEmploiRepository.countByStatut(StatutOffre.PUBLIEE),
                offreEmploiRepository.countByStatut(StatutOffre.CLOTUREE),
                offreEmploiRepository.countByStatut(StatutOffre.ARCHIVEE),
                offreEmploiRepository.countByStatut(StatutOffre.SUSPENDUE)
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Specification<OffreEmploi> filtresCommuns(OffreRechercheCriteres c) {
        return Specification.where(motCle(c.q()))
                .and(localisation(c.localisation()))
                .and(champEgal("typeContrat", c.typeContrat()))
                .and(champEgal("niveauRequis", c.niveauRequis()))
                .and(champEgal("modeTravail", c.modeTravail()))
                .and(competence(c.competence()))
                .and(salaireMin(c.salaireMin()));
    }

    private OffreEmploi findOrThrow(Long id) {
        return offreEmploiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OffreEmploi", "id", id));
    }

    private OffreEmploi findProprietaire(Long id, User recruteurConnecte) {
        OffreEmploi offre = findOrThrow(id);
        if (!offre.getRecruteur().getId().equals(recruteurConnecte.getId())) {
            throw new AccessDeniedException("Vous n'êtes pas l'auteur de cette offre");
        }
        return offre;
    }

    private void verifierStatut(OffreEmploi offre, Set<StatutOffre> autorises, String action) {
        if (!autorises.contains(offre.getStatut())) {
            throw new IllegalStateException(
                    "Une offre au statut " + offre.getStatut() + " ne peut pas être " + action);
        }
    }
}
