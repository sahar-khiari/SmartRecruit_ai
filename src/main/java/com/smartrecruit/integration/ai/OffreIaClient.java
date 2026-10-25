package com.smartrecruit.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.dto.ai.offre.OffreIaCompetencesRequest;
import com.smartrecruit.dto.ai.offre.OffreIaCompetencesResponse;
import com.smartrecruit.dto.ai.offre.OffreIaDescriptionRequest;
import com.smartrecruit.dto.ai.offre.OffreIaDescriptionResponse;
import com.smartrecruit.dto.ai.offre.OffreIaMotsClesRequest;
import com.smartrecruit.dto.ai.offre.OffreIaMotsClesResponse;
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

/**
 * Module 2 : client HTTP vers les endpoints IA des offres du service FastAPI (Cohere).
 * La clé interne reste côté serveur ; Angular n'appelle jamais FastAPI directement.
 */
@Slf4j
@Component
public class OffreIaClient {

    private static final String INTERNAL_KEY_HEADER = "X-AI-Service-Key";

    private final RestClient restClient;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public OffreIaClient(
            @Value("${app.ai-service.enabled:true}") boolean enabled,
            @Value("${app.ai-service.base-url:http://localhost:8001}") String baseUrl,
            @Value("${app.ai-service.api-key:smartrecruit-local-development-key}") String apiKey,
            @Value("${app.ai-service.offre-timeout-seconds:90}") int timeoutSeconds,
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

    public OffreIaDescriptionResponse genererDescription(OffreIaDescriptionRequest request) {
        return post("/api/v1/offres/ia/description", request, OffreIaDescriptionResponse.class);
    }

    public OffreIaCompetencesResponse suggererCompetences(OffreIaCompetencesRequest request) {
        return post("/api/v1/offres/ia/competences", request, OffreIaCompetencesResponse.class);
    }

    public OffreIaMotsClesResponse detecterMotsCles(OffreIaMotsClesRequest request) {
        return post("/api/v1/offres/ia/mots-cles", request, OffreIaMotsClesResponse.class);
    }

    private <T> T post(String uri, Object body, Class<T> responseType) {
        if (!enabled) {
            throw new AiServiceException("Le service IA est désactivé (AI_SERVICE_ENABLED=false)");
        }
        try {
            T response = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new AiServiceException("Réponse vide du service IA", "AI_INVALID_RESPONSE", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw traduire(ex);
        } catch (ResourceAccessException ex) {
            log.warn("Service IA des offres injoignable : {}", ex.getMessage());
            throw new AiServiceException(
                    "Le service IA est injoignable. Vérifiez que FastAPI est démarré.", "AI_UNREACHABLE", ex);
        } catch (RestClientException ex) {
            throw new AiServiceException("Réponse du service IA illisible", "AI_INVALID_RESPONSE", ex);
        }
    }

    /** Récupère le message lisible renvoyé par FastAPI : {"detail": {"code", "message"}}. */
    private AiServiceException traduire(RestClientResponseException ex) {
        String code = "AI_ERROR_" + ex.getStatusCode().value();
        String message = switch (ex.getStatusCode().value()) {
            case 401, 403 -> "Le service IA a refusé la clé interne.";
            case 422 -> "Les informations envoyées à l'IA sont invalides.";
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
        log.warn("Erreur IA des offres | status={} | code={}", ex.getStatusCode().value(), code);
        return new AiServiceException(message, code, ex);
    }
}
