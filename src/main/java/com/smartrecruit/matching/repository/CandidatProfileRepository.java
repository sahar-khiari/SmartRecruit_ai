package com.smartrecruit.matching.repository;

import com.smartrecruit.matching.entity.CandidatProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Accès aux profils candidat du module matching.
 */
@Repository
public interface CandidatProfileRepository extends JpaRepository<CandidatProfile, Long> {

    Optional<CandidatProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
