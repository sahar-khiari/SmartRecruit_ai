package com.smartrecruit.integration.ai;

import com.smartrecruit.dto.ai.AiHealthResponse;
import com.smartrecruit.dto.ai.AiKnowledgeDeleteResponse;
import com.smartrecruit.dto.ai.AiKnowledgeIndexRequest;
import com.smartrecruit.dto.ai.AiKnowledgeIndexResponse;
import com.smartrecruit.dto.ai.AiRagChatRequest;
import com.smartrecruit.dto.ai.AiRagChatResponse;
import com.smartrecruit.dto.ai.AiRetrievalSearchRequest;
import com.smartrecruit.dto.ai.AiRetrievalSearchResponse;

/**
 * Frontière unique entre Spring Boot et le microservice FastAPI interne.
 * Aucun contrôleur Angular n'appelle directement FastAPI.
 */
public interface AiServiceClient {

    AiHealthResponse health();

    AiKnowledgeIndexResponse indexDocument(AiKnowledgeIndexRequest request);

    AiKnowledgeDeleteResponse deleteDocument(Long documentId);

    AiRetrievalSearchResponse search(AiRetrievalSearchRequest request);

    AiRagChatResponse generateRagAnswer(AiRagChatRequest request);
}
