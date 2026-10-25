package com.smartrecruit.matching.repository;

import com.smartrecruit.matching.entity.MatchingCritere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Accès aux critères de matching par offre.
 */
@Repository
public interface MatchingCritereRepository extends JpaRepository<MatchingCritere, Long> {

    List<MatchingCritere> findByOffreId(Long offreId);

    void deleteByOffreId(Long offreId);
}
