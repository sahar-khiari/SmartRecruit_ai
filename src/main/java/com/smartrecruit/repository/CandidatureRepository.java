package com.smartrecruit.repository;

import com.smartrecruit.entity.Candidature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA pour les candidatures.
 */
public interface CandidatureRepository extends JpaRepository<Candidature, Long> {

    List<Candidature> findByOffreId(Long offreId);

    Optional<Candidature> findByOffreIdAndCandidatId(Long offreId, Long candidatId);

    boolean existsByOffreIdAndCandidatId(Long offreId, Long candidatId);

    List<Candidature> findByCandidatId(Long candidatId);
}
