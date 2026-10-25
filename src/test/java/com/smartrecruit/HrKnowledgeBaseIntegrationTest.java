package com.smartrecruit;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartrecruit.dto.request.KnowledgeDocumentCreateRequest;
import com.smartrecruit.dto.request.KnowledgeDocumentStatusRequest;
import com.smartrecruit.dto.request.KnowledgeDocumentUpdateRequest;
import com.smartrecruit.dto.request.UpdateUserRoleRequest;
import com.smartrecruit.entity.KnowledgeCategory;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class HrKnowledgeBaseIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_URL = "/api/admin/hr/knowledge-documents";
    private static final String PUBLIC_URL = "/api/hr/knowledge-documents";

    @Autowired
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Test
    @DisplayName("ADMIN : peut créer une ressource active dans la base de connaissances")
    void admin_canCreateKnowledgeDocument() throws Exception {
        String token = adminToken();

        mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeDocumentCreateRequest(
                                "  Comment postuler à une offre ?  ",
                                "Connectez-vous, ouvrez l'offre puis cliquez sur Postuler.",
                                KnowledgeCategory.FAQ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Comment postuler à une offre ?"))
                .andExpect(jsonPath("$.category").value("FAQ"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdById").isNumber())
                .andExpect(jsonPath("$.createdBy").doesNotExist());

        assertThat(knowledgeDocumentRepository.findAll())
                .singleElement()
                .satisfies(document -> assertThat(document.getCreatedBy().getEmail()).isEqualTo(adminEmail));
    }

    @Test
    @DisplayName("CANDIDATE et RECRUITER : création de ressource refusée (403)")
    void normalUsers_cannotCreateKnowledgeDocument() throws Exception {
        String candidateToken = createCandidateAndGetToken("hr.kb.candidate@test.local");
        String recruiterToken = createRecruiterAndGetToken("hr.kb.recruiter@test.local");
        String body = objectMapper.writeValueAsString(new KnowledgeDocumentCreateRequest(
                "Ressource interdite", "Contenu", KnowledgeCategory.OTHER));

        mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(knowledgeDocumentRepository.count()).isZero();
    }

    @Test
    @DisplayName("ADMIN : filtres catégorie, statut et recherche dans le titre")
    void admin_canFilterKnowledgeDocuments() throws Exception {
        String token = adminToken();
        long faqId = createDocument(token, "Comment postuler ?", KnowledgeCategory.FAQ, true);
        createDocument(token, "Guide entretien Java", KnowledgeCategory.INTERVIEW_GUIDE, true);
        createDocument(token, "Politique de confidentialité", KnowledgeCategory.HR_POLICY, false);

        mockMvc.perform(get(ADMIN_URL)
                        .param("category", "FAQ")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(faqId));

        mockMvc.perform(get(ADMIN_URL)
                        .param("search", "entretien Java")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("INTERVIEW_GUIDE"));

        mockMvc.perform(get(ADMIN_URL)
                        .param("active", "false")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("HR_POLICY"));
    }

    @Test
    @DisplayName("ADMIN : consultation et modification complète d'une ressource")
    void admin_canGetAndUpdateKnowledgeDocument() throws Exception {
        String token = adminToken();
        long id = createDocument(token, "Titre initial", KnowledgeCategory.FAQ, true);

        mockMvc.perform(get(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titre initial"));

        mockMvc.perform(put(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeDocumentUpdateRequest(
                                "Guide de préparation à un entretien Java",
                                "Révisez les collections, le multithreading et Spring Boot.",
                                KnowledgeCategory.INTERVIEW_GUIDE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Guide de préparation à un entretien Java"))
                .andExpect(jsonPath("$.content").value(
                        "Révisez les collections, le multithreading et Spring Boot."))
                .andExpect(jsonPath("$.category").value("INTERVIEW_GUIDE"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get(ADMIN_URL + "/999999")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("ADMIN : désactivation puis réactivation ; un document inactif est invisible publiquement")
    void admin_canToggleStatus_andInactiveDocumentIsHiddenFromUsers() throws Exception {
        String token = adminToken();
        String candidateToken = createCandidateAndGetToken("hr.kb.visibility@test.local");
        long id = createDocument(token, "Visibilité FAQ", KnowledgeCategory.FAQ, true);

        mockMvc.perform(get(PUBLIC_URL)
                        .param("search", "Visibilité FAQ")
                        .header("Authorization", "Bearer " + candidateToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) id)));

        mockMvc.perform(put(ADMIN_URL + "/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeDocumentStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(PUBLIC_URL)
                        .param("search", "Visibilité FAQ")
                        .header("Authorization", "Bearer " + candidateToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content[*].id", not(hasItem((int) id))));

        mockMvc.perform(get(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(put(ADMIN_URL + "/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeDocumentStatusRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get(PUBLIC_URL)
                        .param("search", "Visibilité FAQ")
                        .header("Authorization", "Bearer " + candidateToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Utilisateurs normaux : voient uniquement les documents actifs, jamais les inactifs")
    void normalUsers_onlySeeActiveDocuments() throws Exception {
        String token = adminToken();
        String candidateToken = createCandidateAndGetToken("hr.kb.active.only@test.local");
        String recruiterToken = createRecruiterAndGetToken("hr.kb.active.recruiter@test.local");

        createDocument(token, "Visible candidat", KnowledgeCategory.CANDIDATE_GUIDE, true);
        createDocument(token, "Caché candidat", KnowledgeCategory.CANDIDATE_GUIDE, false);

        mockMvc.perform(get(PUBLIC_URL)
                        .param("search", "candidat")
                        .header("Authorization", "Bearer " + candidateToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Visible candidat"))
                .andExpect(jsonPath("$.content[*].title", not(hasItem("Caché candidat"))));

        mockMvc.perform(get(PUBLIC_URL)
                        .param("search", "candidat")
                        .header("Authorization", "Bearer " + recruiterToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Visible candidat"));
    }

    @Test
    @DisplayName("CANDIDATE et RECRUITER : endpoints ADMIN de la Knowledge Base refusés (403)")
    void normalUsers_cannotAccessAdminKnowledgeEndpoints() throws Exception {
        String candidateToken = createCandidateAndGetToken("hr.kb.admin.denied@test.local");
        String recruiterToken = createRecruiterAndGetToken("hr.kb.admin.recruiter@test.local");
        long id = createDocument(adminToken(), "Document protégé", KnowledgeCategory.OTHER, true);

        mockMvc.perform(get(ADMIN_URL)
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN : suppression réelle d'une ressource")
    void admin_canDeleteKnowledgeDocument() throws Exception {
        String token = adminToken();
        long id = createDocument(token, "À supprimer", KnowledgeCategory.OTHER, true);

        mockMvc.perform(delete(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(knowledgeDocumentRepository.existsById(id)).isFalse();

        mockMvc.perform(get(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Base de connaissances : lecture sans JWT refusée (401)")
    void publicKnowledgeEndpoint_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get(PUBLIC_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("ADMIN : validation des champs obligatoires d'une ressource (400)")
    void admin_createInvalidKnowledgeDocument_returns400() throws Exception {
        mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeDocumentCreateRequest(
                                "  ", "", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").exists())
                .andExpect(jsonPath("$.validationErrors.content").exists())
                .andExpect(jsonPath("$.validationErrors.category").exists());
    }

    private long createDocument(String token, String title, KnowledgeCategory category,
                                boolean active) throws Exception {
        String response = mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeDocumentCreateRequest(
                                title, "Contenu de test pour " + title, category))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        long id = json.get("id").asLong();

        if (!active) {
            mockMvc.perform(put(ADMIN_URL + "/" + id + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new KnowledgeDocumentStatusRequest(false))))
                    .andExpect(status().isOk());
        }

        return id;
    }

    private String createRecruiterAndGetToken(String email) throws Exception {
        createCandidateAndGetToken(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        String adminToken = adminToken();

        mockMvc.perform(put("/api/admin/users/" + user.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRoleRequest(RoleName.RECRUITER))))
                .andExpect(status().isOk());

        return loginAndGetToken(email, DEFAULT_PASSWORD);
    }
}
