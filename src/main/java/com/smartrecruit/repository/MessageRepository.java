package com.smartrecruit.repository;

import com.smartrecruit.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findAllByConversation_IdOrderByCreatedAtAscIdAsc(Long conversationId);

    long countByConversation_Id(Long conversationId);
}
