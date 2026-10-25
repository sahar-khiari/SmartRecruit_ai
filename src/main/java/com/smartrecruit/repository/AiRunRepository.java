package com.smartrecruit.repository;

import com.smartrecruit.entity.AiRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiRunRepository extends JpaRepository<AiRun, Long> {

    List<AiRun> findAllByConversationIdOrderByIdAsc(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
