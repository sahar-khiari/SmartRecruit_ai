package com.smartrecruit.repository;

import com.smartrecruit.entity.MessageSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageSourceRepository extends JpaRepository<MessageSource, Long> {

    List<MessageSource> findAllByMessage_IdOrderByIdAsc(Long messageId);

    List<MessageSource> findAllByMessage_Conversation_IdOrderByMessage_IdAscIdAsc(Long conversationId);

    long countByMessage_Id(Long messageId);
}
