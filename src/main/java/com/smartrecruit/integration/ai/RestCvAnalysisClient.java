package com.smartrecruit.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.dto.ai.AiCvAnalysisResponse;
import com.smartrecruit.exception.CvAnalysisException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

/**
 * Appel HTTP multipart vers POST {base-url}/api/v1/cv/analyze avec l'en-tête X-AI-Service-Key.
 * La clé reste côté serveur : Angular n'appelle jamais le service IA.
 */
@Slf4j
@Component
public class RestCvAnalysisClient implements CvAnalysisClient {

    static final String API_KEY_HEADER = "X-AI-Service-Key";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    @Autowired
    public RestCvAnalysisClient(RestClient.Builder builder,
                                ObjectMapper objectMapper,
                                @Value("${app.ai-service.enabled:true}") boolean enabled,
                                @Value("${app.ai-service.base-url}") String baseUrl,
                                @Value("${app.ai-service.api-key}") String apiKey,
                                @Value("${app.ai-service.cv-timeout-seconds:120}") long timeoutSeconds) {
        this(configure(builder.requestFactory(requestFactory(timeoutSeconds)), baseUrl, apiKey).build(),
                objectMapper, enabled);
    }

    RestCvAnalysisClient(RestClient restClient, ObjectMapper objectMapper, boolean enabled) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    static RestClient.Builder configure(RestClient.Builder builder, String baseUrl, String apiKey) {
        return builder.baseUrl(baseUrl).defaultHeader(API_KEY_HEADER, apiKey);
    }

    private static SimpleClientHttpRequestFactory requestFactory(long timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        // Gemini peut prendre ~20 s, plus un nouvel essai et le modèle de secours.
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return requestFactory;
    }

    @Override
    public AiCvAnalysisResponse analyze(byte[] pdf) {
        if (!enabled) {
            throw new CvAnalysisException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_DISABLED",
                    "Le service d'analyse IA est désactivé");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                // Nom neutre : le nom d'origine peut contenir le nom du candidat.
                return "cv.pdf";
            }
        });

        try {
            return restClient.post()
                    .uri("/api/v1/cv/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw toException(response.getStatusCode(), response.getBody().readAllBytes());
                    })
                    .body(AiCvAnalysisResponse.class);
        } catch (ResourceAccessException ex) {
            log.warn("Service IA injoignable pour l'analyse de CV : {}", ex.getMessage());
            throw new CvAnalysisException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_UNAVAILABLE",
                    "Le service d'analyse IA est injoignable. Veuillez réessayer plus tard");
        }
    }

    /** Traduit les erreurs FastAPI ({code, detail}) en erreurs pour l'utilisateur, sans détail technique. */
    private CvAnalysisException toException(HttpStatusCode status, byte[] body) {
        String code = null;
        String detail = null;
        try {
            JsonNode json = objectMapper.readTree(body);
            code = json.path("code").asText(null);
            detail = json.path("detail").isTextual() ? json.path("detail").asText() : null;
        } catch (IOException ignored) {
            // Corps non JSON : traité comme une erreur générique ci-dessous.
        }
        log.warn("Analyse de CV refusée par le service IA : status={} code={}", status.value(), code);

        if (status.value() == 401 || status.value() == 403) {
            // Problème de configuration serveur (clé interne) : jamais la faute du candidat.
            return new CvAnalysisException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_AUTH",
                    "Le service d'analyse IA est mal configuré. Contactez un administrateur");
        }
        if (status.value() == 413 || status.value() == 422) {
            return new CvAnalysisException(HttpStatus.UNPROCESSABLE_ENTITY,
                    code != null ? code : "INVALID_CV",
                    detail != null ? detail : "Le CV n'a pas pu être lu");
        }
        return new CvAnalysisException(HttpStatus.SERVICE_UNAVAILABLE,
                code != null ? code : "AI_SERVICE_ERROR",
                "L'analyse IA est momentanément indisponible. Veuillez réessayer plus tard");
    }
}
