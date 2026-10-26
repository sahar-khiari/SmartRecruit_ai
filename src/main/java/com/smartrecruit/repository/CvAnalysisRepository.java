package com.smartrecruit.repository;

import com.smartrecruit.entity.CvAnalysis;
import com.smartrecruit.entity.CvAnalysisStatus;
import com.smartrecruit.entity.CvAnalysisType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CvAnalysisRepository extends JpaRepository<CvAnalysis, Long> {

    /** Dernière analyse d'un type donné pour un candidat, tous CV confondus. */
    Optional<CvAnalysis> findFirstByCv_Profile_User_IdAndTypeOrderByCreatedAtDescIdDesc(Long userId, CvAnalysisType type);

    Optional<CvAnalysis> findByIdAndCv_Profile_User_IdAndType(Long id, Long userId, CvAnalysisType type);

    /** Une analyse de ce type est-elle déjà en cours (évite les doubles clics et les appels LLM en double) ? */
    boolean existsByCv_Profile_User_IdAndTypeAndStatusAndCreatedAtAfter(Long userId, CvAnalysisType type,
                                                                         CvAnalysisStatus status, LocalDateTime after);
}
