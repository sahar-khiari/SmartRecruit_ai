package com.smartrecruit.repository;

import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.entity.StatutOffre;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * Critères de recherche multicritère des offres (Module 2).
 * Chaque méthode renvoie {@code null} quand le filtre est vide : Spring l'ignore alors.
 */
public final class OffreEmploiSpecifications {

    private OffreEmploiSpecifications() {
    }

    /** Mot-clé recherché dans le titre, la description, les compétences, les mots-clés et l'entreprise. */
    public static Specification<OffreEmploi> motCle(String q) {
        if (!StringUtils.hasText(q)) {
            return null;
        }
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("titrePoste")), like),
                cb.like(cb.lower(root.get("description")), like),
                cb.like(cb.lower(root.get("competencesRequises")), like),
                cb.like(cb.lower(root.get("motsCles")), like),
                cb.like(cb.lower(root.get("entreprise")), like)
        );
    }

    public static Specification<OffreEmploi> localisation(String localisation) {
        if (!StringUtils.hasText(localisation)) {
            return null;
        }
        String like = "%" + localisation.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("localisation")), like);
    }

    public static Specification<OffreEmploi> champEgal(String champ, String valeur) {
        if (!StringUtils.hasText(valeur)) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.lower(root.get(champ)), valeur.trim().toLowerCase());
    }

    public static Specification<OffreEmploi> competence(String competence) {
        if (!StringUtils.hasText(competence)) {
            return null;
        }
        String like = "%" + competence.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("competencesRequises")), like);
    }

    /** Offres dont le salaire maximum (ou minimum à défaut) atteint le salaire souhaité. */
    public static Specification<OffreEmploi> salaireMin(Integer salaire) {
        if (salaire == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(
                cb.coalesce(root.<Integer>get("salaireMax"), root.<Integer>get("salaireMin")), salaire);
    }

    public static Specification<OffreEmploi> statut(StatutOffre statut) {
        if (statut == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("statut"), statut);
    }

    public static Specification<OffreEmploi> recruteur(Long recruteurId) {
        if (recruteurId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("recruteur").get("id"), recruteurId);
    }

    /** Offres visibles par les candidats : publiées et dont la date limite n'est pas dépassée. */
    public static Specification<OffreEmploi> visiblesCandidats() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("statut"), StatutOffre.PUBLIEE),
                cb.or(cb.isNull(root.get("dateLimite")),
                        cb.greaterThanOrEqualTo(root.get("dateLimite"), LocalDate.now()))
        );
    }
}
