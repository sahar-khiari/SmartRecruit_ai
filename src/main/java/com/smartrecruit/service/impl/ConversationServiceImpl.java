package com.smartrecruit.service.impl;

import com.smartrecruit.dto.request.ConversationCreateRequest;
import com.smartrecruit.dto.request.ConversationUpdateRequest;
import com.smartrecruit.dto.response.ConversationResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.Conversation;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.ConversationNotFoundException;
import com.smartrecruit.mapper.ConversationMapper;
import com.smartrecruit.repository.AiRunRepository;
import com.smartrecruit.repository.ConversationRepository;
import com.smartrecruit.service.ConversationService;
import com.smartrecruit.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final AiRunRepository aiRunRepository;
    private final CurrentUserService currentUserService;
    private final ConversationMapper conversationMapper;

    @Override
    @Transactional
    public ConversationResponse create(ConversationCreateRequest request) {
        User owner = currentUserService.getCurrentUser();
        String title = normalizeTitle(request != null ? request.title() : null);

        Conversation conversation = Conversation.builder()
                .title(title)
                .user(owner)
                .build();

        return conversationMapper.toResponse(conversationRepository.save(conversation));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> listMine(Pageable pageable) {
        User owner = currentUserService.getCurrentUser();
        return PageResponse.from(
                conversationRepository.findAllByUser_Id(owner.getId(), pageable),
                conversationMapper::toResponse
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getMine(Long id) {
        return conversationMapper.toResponse(findOwnedConversation(id));
    }

    @Override
    @Transactional
    public ConversationResponse rename(Long id, ConversationUpdateRequest request) {
        Conversation conversation = findOwnedConversation(id);
        conversation.setTitle(request.title().trim());
        return conversationMapper.toResponse(conversationRepository.save(conversation));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Conversation conversation = findOwnedConversation(id);
        // Les sources suivent les messages via la cascade JPA et la FK SQL.
        // Les traces techniques AiRun n'ont pas de FK : elles sont supprimées ici.
        aiRunRepository.deleteByConversationId(id);
        conversationRepository.delete(conversation);
    }

    private Conversation findOwnedConversation(Long id) {
        User owner = currentUserService.getCurrentUser();
        return conversationRepository.findByIdAndUser_Id(id, owner.getId())
                .orElseThrow(ConversationNotFoundException::new);
    }

    private String normalizeTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : Conversation.DEFAULT_TITLE;
    }
}
