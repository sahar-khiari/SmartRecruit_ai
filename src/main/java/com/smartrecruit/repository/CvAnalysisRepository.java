package com.smartrecruit.repository;

import com.smartrecruit.entity.CvAnalysis;
import com.smartrecruit.entity.CvAnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CvAnalysisRepository extends JpaRepository<CvAnalysis, Long> {

    /** Dernière analyse d'un candidat, tous CV confondus. */
    Optional<CvAnalysis> findFirstByCv_Profile_User_IdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<CvAnalysis> findByIdAndCv_Profile_User_Id(Long id, Long userId);

    /** Une analyse est-elle déjà en cours pour ce candidat (évite les doubles clics et les appels LLM en double) ? */
    boolean existsByCv_Profile_User_IdAndStatusAndCreatedAtAfter(Long userId, CvAnalysisStatus status,
                                                                  LocalDateTime after);
}
