package com.smartrecruit.service.impl;

import com.smartrecruit.dto.request.MessageCreateRequest;
import com.smartrecruit.dto.response.HrMessageResponse;
import com.smartrecruit.entity.Conversation;
import com.smartrecruit.entity.Message;
import com.smartrecruit.entity.MessageRole;
import com.smartrecruit.entity.MessageSource;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.ConversationNotFoundException;
import com.smartrecruit.mapper.MessageMapper;
import com.smartrecruit.repository.ConversationRepository;
import com.smartrecruit.repository.MessageRepository;
import com.smartrecruit.repository.MessageSourceRepository;
import com.smartrecruit.service.CurrentUserService;
import com.smartrecruit.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final ConversationRepository conversationRepository;
    private final CurrentUserService currentUserService;
    private final MessageMapper messageMapper;

    @Override
    @Transactional(readOnly = true)
    public List<HrMessageResponse> listForConversation(Long conversationId) {
        findOwnedConversation(conversationId);

        Map<Long, List<MessageSource>> sourcesByMessage = messageSourceRepository
                .findAllByMessage_Conversation_IdOrderByMessage_IdAscIdAsc(conversationId)
                .stream()
                .collect(Collectors.groupingBy(
                        source -> source.getMessage().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return messageRepository.findAllByConversation_IdOrderByCreatedAtAscIdAsc(conversationId)
                .stream()
                .map(message -> messageMapper.toResponse(
                        message,
                        sourcesByMessage.getOrDefault(message.getId(), List.of())
                ))
                .toList();
    }

    @Override
    @Transactional
    public HrMessageResponse createUserMessage(Long conversationId, MessageCreateRequest request) {
        Conversation conversation = findOwnedConversation(conversationId);

        Message message = Message.builder()
                .conversation(conversation)
                // Le rôle est toujours imposé côté serveur : aucun MessageRole client n'est accepté.
                .role(MessageRole.USER)
                .content(request.content().trim())
                .build();

        return messageMapper.toResponse(messageRepository.save(message));
    }

    private Conversation findOwnedConversation(Long conversationId) {
        User owner = currentUserService.getCurrentUser();
        return conversationRepository.findByIdAndUser_Id(conversationId, owner.getId())
                .orElseThrow(ConversationNotFoundException::new);
    }
}
