package com.smartrecruit.repository;

import com.smartrecruit.entity.CandidateProfile;
import com.smartrecruit.entity.ExperienceLevel;
import com.smartrecruit.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    Optional<CandidateProfile> findByUser_Id(Long userId);

    /**
     * Charge en une requête les profils (et leurs compétences) d'une page de candidats.
     */
    @EntityGraph(attributePaths = "skills")
    List<CandidateProfile> findAllByUser_IdIn(Collection<Long> userIds);

    /**
     * Liste les comptes CANDIDATE, qu'ils aient déjà complété leur profil ou non.
     * {@code search} (en minuscules, entouré de %) porte sur le nom, le titre et les compétences.
     */
    @Query(value = """
            SELECT DISTINCT u FROM User u
            LEFT JOIN CandidateProfile p ON p.user = u
            LEFT JOIN p.skills s
            WHERE u.role.name = com.smartrecruit.entity.RoleName.CANDIDATE
              AND (:level IS NULL OR p.experienceLevel = :level)
              AND (:search IS NULL
                   OR LOWER(u.firstName) LIKE :search
                   OR LOWER(u.lastName) LIKE :search
                   OR LOWER(p.headline) LIKE :search
                   OR LOWER(s.name) LIKE :search)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u) FROM User u
            LEFT JOIN CandidateProfile p ON p.user = u
            LEFT JOIN p.skills s
            WHERE u.role.name = com.smartrecruit.entity.RoleName.CANDIDATE
              AND (:level IS NULL OR p.experienceLevel = :level)
              AND (:search IS NULL
                   OR LOWER(u.firstName) LIKE :search
                   OR LOWER(u.lastName) LIKE :search
                   OR LOWER(p.headline) LIKE :search
                   OR LOWER(s.name) LIKE :search)
            """)
    Page<User> searchCandidates(@Param("search") String search,
                                @Param("level") ExperienceLevel level,
                                Pageable pageable);
}
