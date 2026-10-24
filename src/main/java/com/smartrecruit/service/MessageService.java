package com.smartrecruit.service;

import com.smartrecruit.dto.request.MessageCreateRequest;
import com.smartrecruit.dto.response.HrMessageResponse;

import java.util.List;

public interface MessageService {

    List<HrMessageResponse> listForConversation(Long conversationId);

    HrMessageResponse createUserMessage(Long conversationId, MessageCreateRequest request);
}
