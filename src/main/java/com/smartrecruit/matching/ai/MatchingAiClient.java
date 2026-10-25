package com.smartrecruit.matching.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client HTTP vers les endpoints de matching FastAPI (layers 1 et 2).
 *
 * Circuit-breaker léger :
 *   après CIRCUIT_FAILURE_THRESHOLD échecs consécutifs, les layers 1/2 sont
 *   ignorées pendant CIRCUIT_COOLDOWN_SECONDS secondes, puis réessayées.
 *
 * Ce client est entièrement isolé du client AI existant (RestAiServiceClient).
 * Il utilise ses propres timeouts et son propre endpoint.
 */
@Component
public class MatchingAiClient {

    private static final Logger log = LoggerFactory.getLogger(MatchingAiClient.class);

    /** Nombre d'échecs consécutifs avant ouverture du circuit-breaker. */
    private static final int    CIRCUIT_FAILURE_THRESHOLD = 3;
    /** Durée de refroidissement après ouverture (secondes). */
    private static final long   CIRCUIT_COOLDOWN_SECONDS  = 60;

    private static final String INTERNAL_KEY_HEADER = "X-AI-Service-Key";
    private static final String EMBED_RANK_PATH     = "/api/v1/matching/embed-rank";
    private static final String LLM_JUDGE_PATH      = "/api/v1/matching/llm-judge";

    private final RestClient restClient;

    // Circuit-breaker léger (sans bibliothèque externe)
    private final AtomicInteger  consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> circuitOpenedAt = new AtomicReference<>(null);

    /** URL de base du service FastAPI (partagée avec les autres modules). */
    private final String baseUrl;

    /** LLM configurable pour le module matching — désactivé par défaut. */
    @Value("${matching.llm.enabled:false}")
    private boolean llmEnabled;

    @Value("${matching.llm.model:llama3}")
    private String llmModel;

    @Value("${matching.llm.base-url:http://localhost:11434/v1}")
    private String llmBaseUrl;

    public MatchingAiClient(
            @Value("${app.ai-service.base-url:http://localhost:8001}") String baseUrl,
            @Value("${app.ai-service.api-key:smartrecruit-local-development-key}") String apiKey,
            @Value("${matching.ai.timeout-seconds:10}") int timeoutSeconds) {

        this.baseUrl = baseUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration t = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        factory.setConnectTimeout(t);
        factory.setReadTimeout(t);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(INTERNAL_KEY_HEADER, apiKey)
                .build();
    }

    /**
     * Appelle FastAPI pour le classement par embeddings (Layer 1).
     * Retourne un résultat indisponible si FastAPI est down ou si le circuit est ouvert.
     */
    public EmbedRankResponse embedRank(EmbedRankRequest request) {
        if (isCircuitOpen()) {
            log.debug("Circuit matching ouvert — layer 1 ignorée");
            return unavailableEmbedRank("Service IA temporairement indisponible (circuit-breaker)");
        }
        try {
            EmbedRankResponse resp = restClient.post()
                    .uri(EMBED_RANK_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EmbedRankResponse.class);
            onSuccess();
            return resp != null ? resp : unavailableEmbedRank("Réponse vide");
        } catch (RestClientException ex) {
            onFailure(ex);
            return unavailableEmbedRank("FastAPI indisponible : " + ex.getClass().getSimpleName());
        }
    }

    /**
     * Appelle FastAPI pour le jugement LLM (Layer 2).
     * Retourne un résultat indisponible si LLM désactivé ou FastAPI down.
     */
    public LlmJudgeResponse llmJudge(LlmJudgeRequest request) {
        if (!llmEnabled) {
            return unavailableLlm("LLM matching désactivé (MATCHING_LLM_ENABLED=false)");
        }
        if (isCircuitOpen()) {
            log.debug("Circuit matching ouvert — layer 2 ignorée");
            return unavailableLlm("Service IA temporairement indisponible (circuit-breaker)");
        }
        try {
            LlmJudgeResponse resp = restClient.post()
                    .uri(LLM_JUDGE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(LlmJudgeResponse.class);
            onSuccess();
            return resp != null ? resp : unavailableLlm("Réponse vide");
        } catch (RestClientException ex) {
            onFailure(ex);
            return unavailableLlm("FastAPI indisponible : " + ex.getClass().getSimpleName());
        }
    }

    public boolean isLlmEnabled() { return llmEnabled; }
    public String  getLlmModel()  { return llmModel;   }
    public String  getLlmBaseUrl(){ return llmBaseUrl;  }
    public boolean isCircuitOpen() {
        Instant openedAt = circuitOpenedAt.get();
        if (openedAt == null) return false;
        if (Duration.between(openedAt, Instant.now()).getSeconds() >= CIRCUIT_COOLDOWN_SECONDS) {
            circuitOpenedAt.set(null); // Réinitialisation après refroidissement
            consecutiveFailures.set(0);
            log.info("[Matching] Circuit-breaker réinitialisé après refroidissement");
            return false;
        }
        return true;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        circuitOpenedAt.set(null);
    }

    private void onFailure(Exception ex) {
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("[Matching] Échec appel FastAPI ({}/{}): {}", failures,
                CIRCUIT_FAILURE_THRESHOLD, ex.getClass().getSimpleName());
        if (failures >= CIRCUIT_FAILURE_THRESHOLD) {
            circuitOpenedAt.compareAndSet(null, Instant.now());
            log.warn("[Matching] Circuit-breaker ouvert — layers 1/2 suspendues {}s",
                    CIRCUIT_COOLDOWN_SECONDS);
        }
    }

    private EmbedRankResponse unavailableEmbedRank(String msg) {
        return new EmbedRankResponse(false, List.of(), msg);
    }

    private LlmJudgeResponse unavailableLlm(String msg) {
        return new LlmJudgeResponse(false, null, null, null, null, null,
                0.0, null, msg);
    }
}
