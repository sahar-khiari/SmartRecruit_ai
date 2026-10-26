package com.smartrecruit.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.dto.response.OffreComparaisonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Module 2 : appel de POST /api/v1/offres/ia/comparer (FastAPI, Cohere) avec la clé interne.
 * Même configuration et même traduction des erreurs que OffreIaClient (non modifié).
 */
@Slf4j
@Component
public class OffreComparaisonClient {

    private static final String INTERNAL_KEY_HEADER = "X-AI-Service-Key";

    private final RestClient restClient;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public OffreComparaisonClient(
            @Value("${app.ai-service.enabled:true}") boolean enabled,
            @Value("${app.ai-service.base-url:http://localhost:8001}") String baseUrl,
            @Value("${app.ai-service.api-key:smartrecruit-local-development-key}") String apiKey,
            // Cohere peut répondre en ~50 s avec une clé d'essai, plus les nouveaux essais.
            @Value("${app.ai-service.offre-comparaison-timeout-seconds:150}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(INTERNAL_KEY_HEADER, apiKey)
                .build();
    }

    /** {@code body} : {"offres": [...], "profil": "..."} au format camelCase attendu par FastAPI. */
    public OffreComparaisonResponse comparer(Map<String, Object> body) {
        if (!enabled) {
            throw new AiServiceException("Le service IA est désactivé (AI_SERVICE_ENABLED=false)");
        }
        try {
            OffreComparaisonResponse response = restClient.post()
                    .uri("/api/v1/offres/ia/comparer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OffreComparaisonResponse.class);
            if (response == null) {
                throw new AiServiceException("Réponse vide du service IA", "AI_INVALID_RESPONSE", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw traduire(ex);
        } catch (ResourceAccessException ex) {
            log.warn("Comparaison d'offres : service IA injoignable : {}", ex.getMessage());
            throw new AiServiceException(
                    "Le service IA est injoignable. Vérifiez que FastAPI est démarré.", "AI_UNREACHABLE", ex);
        } catch (RestClientException ex) {
            throw new AiServiceException("Réponse du service IA illisible", "AI_INVALID_RESPONSE", ex);
        }
    }

    private AiServiceException traduire(RestClientResponseException ex) {
        String code = "AI_ERROR_" + ex.getStatusCode().value();
        String message = switch (ex.getStatusCode().value()) {
            case 401, 403 -> "Le service IA a refusé la clé interne.";
            case 422 -> "Les offres envoyées à l'IA sont invalides.";
            case 429 -> "Quota de l'IA atteint, réessayez dans quelques instants.";
            case 504 -> "L'IA a mis trop de temps à répondre, réessayez.";
            default -> "L'IA est temporairement indisponible.";
        };
        try {
            JsonNode detail = objectMapper.readTree(ex.getResponseBodyAsString()).path("detail");
            if (detail.hasNonNull("message")) {
                message = detail.get("message").asText();
                code = detail.path("code").asText(code);
            }
        } catch (Exception ignored) {
            // Corps non JSON : on garde le message par défaut.
        }
        log.warn("Comparaison d'offres en erreur | status={} | code={}", ex.getStatusCode().value(), code);
        return new AiServiceException(message, code, ex);
    }
}
