package com.smartrecruit.repository;

import com.smartrecruit.entity.OffreEmploi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository JPA pour les offres d'emploi.
 */
public interface OffreEmploiRepository extends JpaRepository<OffreEmploi, Long> {

    Page<OffreEmploi> findByActiveTrue(Pageable pageable);

    Page<OffreEmploi> findByRecruteurId(Long recruteurId, Pageable pageable);

    boolean existsByIdAndRecruteurId(Long id, Long recruteurId);
}
