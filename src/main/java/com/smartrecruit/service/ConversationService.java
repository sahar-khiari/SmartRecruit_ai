package com.smartrecruit.service;

import com.smartrecruit.dto.request.ConversationCreateRequest;
import com.smartrecruit.dto.request.ConversationUpdateRequest;
import com.smartrecruit.dto.response.ConversationResponse;
import com.smartrecruit.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ConversationService {

    ConversationResponse create(ConversationCreateRequest request);

    PageResponse<ConversationResponse> listMine(Pageable pageable);

    ConversationResponse getMine(Long id);

    ConversationResponse rename(Long id, ConversationUpdateRequest request);

    void delete(Long id);
}
