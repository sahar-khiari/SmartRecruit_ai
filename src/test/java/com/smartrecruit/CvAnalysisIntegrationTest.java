package com.smartrecruit;

import com.smartrecruit.dto.ai.AiCvAnalysisResponse;
import com.smartrecruit.dto.ai.AiCvCoachResponse;
import com.smartrecruit.dto.request.CvAnalysisApplyRequest;
import com.smartrecruit.entity.CvAnalysis;
import com.smartrecruit.entity.CvAnalysisStatus;
import com.smartrecruit.exception.CvAnalysisException;
import com.smartrecruit.integration.ai.CvAnalysisClient;
import com.smartrecruit.repository.CvAnalysisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Analyse de CV de bout en bout côté Spring ; le service IA (FastAPI) est simulé.
 */
@Transactional
class CvAnalysisIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @MockBean
    private CvAnalysisClient cvAnalysisClient;

    @Autowired
    private CvAnalysisRepository analysisRepository;

    @Test
    @DisplayName("L'analyse est enregistrée COMPLETED avec sa traçabilité, sans modifier le profil")
    void analyze_success_savesTraceAndKeepsProfile() throws Exception {
        String token = candidateWithCv("analyse@test.local");
        when(cvAnalysisClient.analyze(any())).thenReturn(aiResponse());

        perform(token, post("/api/candidates/me/cv/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.proposal.headline").value("Développeur Full Stack"))
                .andExpect(jsonPath("$.proposal.experienceLevel").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.proposal.skills[0].original").value("ReactJS"))
                .andExpect(jsonPath("$.proposal.skills[0].name").value("React"))
                .andExpect(jsonPath("$.trace.model").value("gemini-3.5-flash"))
                .andExpect(jsonPath("$.trace.promptVersion").value("cv-extract-v1"))
                .andExpect(jsonPath("$.trace.inputTokens").value(465))
                .andExpect(jsonPath("$.appliedAt").doesNotExist());

        // « L'IA propose, l'humain décide » : rien n'est copié dans le profil à ce stade.
        perform(token, get("/api/candidates/me"))
                .andExpect(jsonPath("$.headline").doesNotExist())
                .andExpect(jsonPath("$.skills.length()").value(0));

        perform(token, get("/api/candidates/me/cv/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Le candidat valide une proposition corrigée : compétences IA marquées AI, ajouts MANUAL")
    void apply_editedProposal_updatesProfile() throws Exception {
        String token = candidateWithCv("valide@test.local");
        when(cvAnalysisClient.analyze(any())).thenReturn(aiResponse());
        perform(token, put("/api/candidates/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"+216 22 333 444\", \"city\": \"Tunis\"}"))
                .andExpect(status().isOk());
        Long analysisId = analysisId(perform(token, post("/api/candidates/me/cv/analyze")));

        // Le candidat retire Docker, ajoute Kubernetes et corrige ses années.
        CvAnalysisApplyRequest request = new CvAnalysisApplyRequest(analysisId, "Développeur Full Stack Java",
                "Résumé relu par le candidat.", 6, List.of("React", "Spring Boot", "Kubernetes"));

        perform(token, post("/api/candidates/me/cv/analysis/apply").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Développeur Full Stack Java"))
                .andExpect(jsonPath("$.yearsOfExperience").value(6))
                .andExpect(jsonPath("$.experienceLevel").value("SENIOR"))
                .andExpect(jsonPath("$.city").value("Tunis"))
                .andExpect(jsonPath("$.phone").value("+216 22 333 444"))
                .andExpect(jsonPath("$.skills[0].name").value("Kubernetes"))
                .andExpect(jsonPath("$.skills[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.skills[1].name").value("React"))
                .andExpect(jsonPath("$.skills[1].source").value("AI"))
                .andExpect(jsonPath("$.skills.length()").value(3));

        assertThat(analysisRepository.findById(analysisId).orElseThrow().getAppliedAt()).isNotNull();
    }

    @Test
    @DisplayName("Un échec du service IA est enregistré FAILED avec son code, et renvoie un message clair")
    void analyze_aiFailure_savesFailedAnalysis() throws Exception {
        String token = candidateWithCv("echec@test.local");
        when(cvAnalysisClient.analyze(any())).thenThrow(new CvAnalysisException(
                HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE", "L'analyse IA est momentanément indisponible"));

        perform(token, post("/api/candidates/me/cv/analyze"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("L'analyse IA est momentanément indisponible"));

        perform(token, get("/api/candidates/me/cv/analysis"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.proposal").doesNotExist());
    }

    @Test
    @DisplayName("Sans CV déposé : 404 et aucun appel au service IA")
    void analyze_withoutCv_returns404() throws Exception {
        String token = createCandidateAndGetToken("sans.cv.ia@test.local");

        perform(token, post("/api/candidates/me/cv/analyze")).andExpect(status().isNotFound());

        verify(cvAnalysisClient, never()).analyze(any());
    }

    @Test
    @DisplayName("Une analyse PENDING récente bloque une deuxième analyse (409)")
    void analyze_whilePending_returns409() throws Exception {
        String token = candidateWithCv("double.clic@test.local");
        when(cvAnalysisClient.analyze(any())).thenReturn(aiResponse());
        Long analysisId = analysisId(perform(token, post("/api/candidates/me/cv/analyze")));
        CvAnalysis analysis = analysisRepository.findById(analysisId).orElseThrow();
        analysis.setStatus(CvAnalysisStatus.PENDING);
        analysisRepository.saveAndFlush(analysis);

        perform(token, post("/api/candidates/me/cv/analyze")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Impossible d'appliquer l'analyse d'un autre candidat (404) ou une analyse en échec (409)")
    void apply_rules() throws Exception {
        String owner = candidateWithCv("proprio.ia@test.local");
        when(cvAnalysisClient.analyze(any())).thenReturn(aiResponse());
        Long analysisId = analysisId(perform(owner, post("/api/candidates/me/cv/analyze")));
        String body = objectMapper.writeValueAsString(
                new CvAnalysisApplyRequest(analysisId, null, null, 1, List.of("Java")));

        perform(createCandidateAndGetToken("intrus.ia@test.local"), post("/api/candidates/me/cv/analysis/apply")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());

        CvAnalysis analysis = analysisRepository.findById(analysisId).orElseThrow();
        analysis.setStatus(CvAnalysisStatus.FAILED);
        analysisRepository.saveAndFlush(analysis);
        perform(owner, post("/api/candidates/me/cv/analysis/apply").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Un administrateur ne peut pas lancer d'analyse pour lui-même (403)")
    void admin_cannotAnalyze() throws Exception {
        perform(adminToken(), post("/api/candidates/me/cv/analyze")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Coach CV : score et conseils enregistrés (type COACHING), sans toucher à l'extraction ni au profil")
    void coach_success_isTracedSeparately() throws Exception {
        String token = candidateWithCv("coach@test.local");
        when(cvAnalysisClient.coach(any())).thenReturn(coachResponse());

        perform(token, post("/api/candidates/me/cv/coach"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.score").value(65))
                .andExpect(jsonPath("$.criteria[0].label").value("Rubrique Formation"))
                .andExpect(jsonPath("$.criteria[0].advice").exists())
                .andExpect(jsonPath("$.improvements[0].title").value("Ajoutez une rubrique Formation"))
                .andExpect(jsonPath("$.rewrites[0].after").value("Conception de [nombre] API REST"))
                .andExpect(jsonPath("$.rejectedRewrites").value(1))
                .andExpect(jsonPath("$.trace.promptVersion").value("cv-coach-v1"));

        perform(token, get("/api/candidates/me/cv/coach"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(65));

        // Le coaching n'est pas une analyse d'extraction : rien à valider dans le profil.
        perform(token, get("/api/candidates/me/cv/analysis")).andExpect(status().isNotFound());
        verify(cvAnalysisClient, never()).analyze(any());
    }

    @Test
    @DisplayName("Coach CV : un échec du service IA est enregistré FAILED et renvoie 503")
    void coach_failure_isRecorded() throws Exception {
        String token = candidateWithCv("coach.echec@test.local");
        when(cvAnalysisClient.coach(any())).thenThrow(new CvAnalysisException(
                HttpStatus.SERVICE_UNAVAILABLE, "LLM_QUOTA", "L'analyse IA est momentanément indisponible"));

        perform(token, post("/api/candidates/me/cv/coach")).andExpect(status().isServiceUnavailable());

        perform(token, get("/api/candidates/me/cv/coach"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("LLM_QUOTA"))
                .andExpect(jsonPath("$.score").doesNotExist());
    }

    private static AiCvCoachResponse coachResponse() {
        return new AiCvCoachResponse(
                "cv-coach-v1", 65,
                List.of(new AiCvCoachResponse.Criterion("education", "Rubrique Formation", 0, 10, false,
                        "Ajoutez une rubrique « Formation »."),
                        new AiCvCoachResponse.Criterion("experience", "Rubrique Expérience", 15, 15, true, null)),
                List.of("Expérience solide sur Spring Boot"),
                List.of(new AiCvCoachResponse.Improvement("Ajoutez une rubrique Formation", "Indiquez votre diplôme.")),
                List.of(new AiCvCoachResponse.Rewrite("Développement d'API", "Conception de [nombre] API REST",
                        "Résultat mesurable")),
                1,
                new AiCvAnalysisResponse.Llm("gemini-3.5-flash-lite", true, "cv-coach-v1", List.of(), 600, 300),
                Map.of("advice", 1200));
    }

    private String candidateWithCv(String email) throws Exception {
        String token = createCandidateAndGetToken(email);
        mockMvc.perform(multipart("/api/candidates/me/cv")
                        .file(new MockMultipartFile("file", "cv.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        return token;
    }

    private ResultActions perform(String token,
                                  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private Long analysisId(ResultActions result) throws Exception {
        String json = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private static AiCvAnalysisResponse aiResponse() {
        return new AiCvAnalysisResponse(
                "cv-pipeline-v3", 1, false,
                new AiCvAnalysisResponse.Anonymization(5, List.of("EMAIL", "NAME", "PHONE")),
                new AiCvAnalysisResponse.Profile(
                        "Développeur Full Stack", "Le candidat est développeur full stack.", 3, "INTERMEDIATE",
                        List.of("React", "Spring Boot", "Docker"),
                        List.of(new AiCvAnalysisResponse.SkillDetail("ReactJS", "React", "FRAMEWORK", "EXACT", 100),
                                new AiCvAnalysisResponse.SkillDetail("Spring-boot", "Spring Boot", "FRAMEWORK", "EXACT", 100),
                                new AiCvAnalysisResponse.SkillDetail("Docker", "Docker", "DEVOPS", "EXACT", 100)),
                        "skills-ref-v1", List.of("Autonomie"), List.of("Anglais (B2)")),
                new AiCvAnalysisResponse.Llm("gemini-3.5-flash", false, "cv-extract-v1",
                        List.of(new AiCvAnalysisResponse.Attempt("gemini-3.5-flash", null, 18000)), 465, 97),
                Map.of("extraction", 18000));
    }
}
