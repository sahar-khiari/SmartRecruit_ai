package com.smartrecruit.exception;

/**
 * Aucun compte CANDIDATE pour cet identifiant (inexistant, ou compte ADMIN / RECRUITER). HTTP 404.
 * Hérite de {@link ResourceNotFoundException} : géré par le gestionnaire global existant.
 */
public class CandidateNotFoundException extends ResourceNotFoundException {

    public CandidateNotFoundException(Long id) {
        super("Candidat introuvable pour id = " + id);
    }
}
