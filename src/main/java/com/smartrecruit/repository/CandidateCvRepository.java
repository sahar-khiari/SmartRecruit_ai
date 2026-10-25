package com.smartrecruit.repository;

import com.smartrecruit.entity.CandidateCv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateCvRepository extends JpaRepository<CandidateCv, Long> {

    /** CV courant d'un candidat : le plus récent (l'id départage deux dépôts dans la même seconde). */
    Optional<CandidateCv> findFirstByProfile_User_IdOrderByUploadedAtDescIdDesc(Long userId);

    @Query("SELECT cv.storageKey FROM CandidateCv cv WHERE cv.profile.user.id = :userId")
    List<String> findStorageKeysByUserId(@Param("userId") Long userId);
}
