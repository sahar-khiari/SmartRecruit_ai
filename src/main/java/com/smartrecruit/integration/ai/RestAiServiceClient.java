package com.smartrecruit.integration.ai;

import com.smartrecruit.dto.ai.AiHealthResponse;
import com.smartrecruit.dto.ai.AiKnowledgeDeleteResponse;
import com.smartrecruit.dto.ai.AiKnowledgeIndexRequest;
import com.smartrecruit.dto.ai.AiKnowledgeIndexResponse;
import com.smartrecruit.dto.ai.AiRagChatRequest;
import com.smartrecruit.dto.ai.AiRagChatResponse;
import com.smartrecruit.dto.ai.AiRetrievalSearchRequest;
import com.smartrecruit.dto.ai.AiRetrievalSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;

/**
 * Client HTTP synchrone du service FastAPI. La clé interne est lue côté serveur
 * uniquement et n'est jamais renvoyée à Angular ni journalisée.
 */
@Component
public class RestAiServiceClient implements AiServiceClient {

    private static final String INTERNAL_KEY_HEADER = "X-AI-Service-Key";

    private final RestClient restClient;
    private final RestClient chatRestClient;

    public RestAiServiceClient(
            @Value("${app.ai-service.base-url:http://localhost:8001}") String baseUrl,
            @Value("${app.ai-service.api-key:smartrecruit-local-development-key}") String apiKey,
            @Value("${app.ai-service.timeout-seconds:30}") int timeoutSeconds,
            @Value("${app.ai-service.chat-timeout-seconds:120}") int chatTimeoutSeconds) {
        this.restClient = buildClient(baseUrl, apiKey, timeoutSeconds);
        this.chatRestClient = buildClient(baseUrl, apiKey, chatTimeoutSeconds);
    }

    @Override
    public AiHealthResponse health() {
        try {
            return restClient.get()
                    .uri("/health")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AiHealthResponse.class);
        } catch (RestClientException ex) {
            throw mapException("L'état du service d'indexation est indisponible", ex);
        }
    }

    @Override
    public AiKnowledgeIndexResponse indexDocument(AiKnowledgeIndexRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/knowledge/index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiKnowledgeIndexResponse.class);
        } catch (RestClientException ex) {
            throw mapException("Le service d'indexation est indisponible", ex);
        }
    }

    @Override
    public AiKnowledgeDeleteResponse deleteDocument(Long documentId) {
        try {
            return restClient.delete()
                    .uri("/api/v1/knowledge/{documentId}", documentId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AiKnowledgeDeleteResponse.class);
        } catch (RestClientException ex) {
            throw mapException("La suppression vectorielle est indisponible", ex);
        }
    }

    @Override
    public AiRetrievalSearchResponse search(AiRetrievalSearchRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/retrieval/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiRetrievalSearchResponse.class);
        } catch (RestClientException ex) {
            throw mapException("La recherche sémantique est indisponible", ex);
        }
    }

    @Override
    public AiRagChatResponse generateRagAnswer(AiRagChatRequest request) {
        try {
            return chatRestClient.post()
                    .uri("/api/v1/chat/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiRagChatResponse.class);
        } catch (RestClientException ex) {
            throw mapException(
                    "L'Assistant RH est temporairement indisponible. "
                            + "Votre message a été enregistré. Vous pouvez réessayer.",
                    ex,
                    true
            );
        }
    }

    private RestClient buildClient(String baseUrl, String apiKey, int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(INTERNAL_KEY_HEADER, apiKey)
                .build();
    }

    private AiServiceException mapException(String fallbackMessage, RestClientException cause) {
        return mapException(fallbackMessage, cause, false);
    }

    private AiServiceException mapException(
            String fallbackMessage,
            RestClientException cause,
            boolean generationOperation
    ) {
        if (cause instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return switch (status) {
                case 401, 403 -> new AiServiceException(
                        "Le service interne a refusé la requête.",
                        "AI_INTERNAL_AUTH",
                        cause
                );
                case 422 -> new AiServiceException(
                        "La question n'a pas pu être traitée.",
                        "AI_INVALID_REQUEST",
                        cause
                );
                case 502 -> new AiServiceException(
                        "La réponse du modèle local est invalide.",
                        "INVALID_AI_RESPONSE",
                        cause
                );
                case 503 -> new AiServiceException(
                        "Le modèle local n'est pas disponible.",
                        "OLLAMA_UNAVAILABLE",
                        cause
                );
                case 504 -> new AiServiceException(
                        "La génération a dépassé le délai autorisé.",
                        "GENERATION_TIMEOUT",
                        cause
                );
                default -> new AiServiceException(
                        fallbackMessage,
                        "AI_SERVICE_UNAVAILABLE",
                        cause
                );
            };
        }

        if (generationOperation
                && cause instanceof ResourceAccessException
                && isTimeout(cause)) {
            return new AiServiceException(
                    "La génération a dépassé le délai autorisé.",
                    "GENERATION_TIMEOUT",
                    cause
            );
        }

        // Le détail de l'exception reste côté serveur, jamais dans la réponse HTTP.
        return new AiServiceException(fallbackMessage, "AI_SERVICE_UNAVAILABLE", cause);
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
