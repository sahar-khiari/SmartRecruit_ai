package com.smartrecruit.matching.repository;

import com.smartrecruit.matching.entity.MatchingRecommandation;
import com.smartrecruit.matching.entity.StatutRecommandation;
import com.smartrecruit.matching.entity.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Accès aux recommandations de matching.
 */
@Repository
public interface MatchingRecommandationRepository extends JpaRepository<MatchingRecommandation, Long> {

    List<MatchingRecommandation> findByTargetTypeAndTargetIdOrderByRangAsc(
            TargetType targetType, Long targetId);

    List<MatchingRecommandation> findByTargetTypeAndTargetIdAndStatutOrderByRangAsc(
            TargetType targetType, Long targetId, StatutRecommandation statut);

    void deleteByTargetTypeAndTargetId(TargetType targetType, Long targetId);

    void deleteByTargetTypeAndItemId(TargetType targetType, Long itemId);

    void deleteByTargetTypeAndTargetIdAndItemId(TargetType targetType, Long targetId, Long itemId);
}
