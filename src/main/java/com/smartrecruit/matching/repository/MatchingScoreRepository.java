package com.smartrecruit.matching.repository;

import com.smartrecruit.matching.entity.MatchingScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux scores de matching.
 */
@Repository
public interface MatchingScoreRepository extends JpaRepository<MatchingScore, Long> {

    Optional<MatchingScore> findByCandidateIdAndOffreId(Long candidateId, Long offreId);

    /**
     * Classement des candidats pour une offre, du meilleur au moins bon.
     */
    @Query("SELECT s FROM MatchingScore s WHERE s.offreId = :offreId ORDER BY s.finalScore DESC")
    List<MatchingScore> findRankingByOffreId(Long offreId);

    List<MatchingScore> findByCandidateIdOrderByFinalScoreDesc(Long candidateId);

    void deleteByCandidateIdAndOffreId(Long candidateId, Long offreId);
}
