package com.smartrecruit.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.dto.ai.AiCvAnalysisResponse;
import com.smartrecruit.exception.CvAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestCvAnalysisClientTest {

    private static final byte[] PDF = "%PDF-1.7 contenu".getBytes();

    private MockRestServiceServer server;
    private RestCvAnalysisClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestCvAnalysisClient.configure(RestClient.builder(), "http://ai.test", "cle-interne");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestCvAnalysisClient(builder.build(), new ObjectMapper(), true);
    }

    @Test
    @DisplayName("Envoie le PDF en multipart avec la clé interne et lit la réponse snake_case")
    void analyze_success() {
        server.expect(requestTo("http://ai.test/api/v1/cv/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-AI-Service-Key", "cle-interne"))
                .andExpect(content().string(containsString("filename=\"cv.pdf\"")))
                .andRespond(withSuccess("""
                        {"pipeline_version": "cv-pipeline-v3", "anonymized_text": "[NAME] ...",
                         "profile": {"headline": "Dev Java", "years_of_experience": 3, "skills": ["Java"],
                                     "skills_details": [{"original": "JAVA", "name": "Java", "category": "LANGUAGE",
                                                         "method": "EXACT", "score": 100}]},
                         "llm": {"model": "gemini-3.5-flash", "fallback_used": false, "prompt_version": "cv-extract-v1",
                                 "input_tokens": 465, "output_tokens": 97}}
                        """, MediaType.APPLICATION_JSON));

        AiCvAnalysisResponse response = client.analyze(PDF);

        assertThat(response.profile().yearsOfExperience()).isEqualTo(3);
        assertThat(response.profile().skillsDetails().get(0).original()).isEqualTo("JAVA");
        assertThat(response.llm().model()).isEqualTo("gemini-3.5-flash");
        server.verify();
    }

    @Test
    @DisplayName("Coach CV : appelle /api/v1/cv/coach et lit le score et les conseils")
    void coach_success() {
        server.expect(requestTo("http://ai.test/api/v1/cv/coach"))
                .andExpect(header("X-AI-Service-Key", "cle-interne"))
                .andRespond(withSuccess("""
                        {"prompt_version": "cv-coach-v1", "score": 65,
                         "criteria": [{"id": "education", "label": "Rubrique Formation", "points": 0,
                                       "max_points": 10, "passed": false, "advice": "Ajoutez une rubrique"}],
                         "strengths": ["Spring Boot"], "improvements": [{"title": "Formation", "detail": "..."}],
                         "rewrites": [], "rejected_rewrites": 0,
                         "llm": {"model": "gemini-3.5-flash", "fallback_used": false, "prompt_version": "cv-coach-v1"}}
                        """, MediaType.APPLICATION_JSON));

        var response = client.coach(PDF);

        assertThat(response.score()).isEqualTo(65);
        assertThat(response.criteria().get(0).maxPoints()).isEqualTo(10);
        assertThat(response.llm().promptVersion()).isEqualTo("cv-coach-v1");
        server.verify();
    }

    @Test
    @DisplayName("Un CV refusé par FastAPI (422) garde son code et son message")
    void analyze_invalidCv_returns422WithCode() {
        server.expect(requestTo("http://ai.test/api/v1/cv/analyze"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\": \"NO_TEXT\", \"detail\": \"Le PDF ne contient pas de texte exploitable\"}"));

        assertThatThrownBy(() -> client.analyze(PDF))
                .isInstanceOfSatisfying(CvAnalysisException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getCode()).isEqualTo("NO_TEXT");
                    assertThat(ex.getMessage()).contains("texte exploitable");
                });
    }

    @Test
    @DisplayName("Modèles indisponibles (503) : message générique, code conservé")
    void analyze_llmDown_returns503() {
        server.expect(requestTo("http://ai.test/api/v1/cv/analyze"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\": \"LLM_UNAVAILABLE\", \"detail\": \"détail interne\"}"));

        assertThatThrownBy(() -> client.analyze(PDF))
                .isInstanceOfSatisfying(CvAnalysisException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo("LLM_UNAVAILABLE");
                    assertThat(ex.getMessage()).doesNotContain("détail interne");
                });
    }

    @Test
    @DisplayName("Clé interne refusée : erreur de configuration serveur, jamais 401 pour le candidat")
    void analyze_wrongInternalKey_returns503() {
        server.expect(requestTo("http://ai.test/api/v1/cv/analyze")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.analyze(PDF))
                .isInstanceOfSatisfying(CvAnalysisException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo("AI_SERVICE_AUTH");
                });
    }

    @Test
    @DisplayName("Service IA injoignable : 503 AI_SERVICE_UNAVAILABLE")
    void analyze_unreachable_returns503() {
        server.expect(requestTo("http://ai.test/api/v1/cv/analyze"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.analyze(PDF))
                .isInstanceOfSatisfying(CvAnalysisException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("AI_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Service désactivé : aucun appel réseau")
    void analyze_disabled() {
        RestCvAnalysisClient disabled = new RestCvAnalysisClient(RestClient.create(), new ObjectMapper(), false);

        assertThatThrownBy(() -> disabled.analyze(PDF))
                .isInstanceOfSatisfying(CvAnalysisException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("AI_SERVICE_DISABLED"));
    }
}
