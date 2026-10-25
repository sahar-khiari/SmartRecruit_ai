package com.smartrecruit;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartrecruit.dto.ai.AiKnowledgeDeleteResponse;
import com.smartrecruit.dto.ai.AiKnowledgeIndexRequest;
import com.smartrecruit.dto.ai.AiKnowledgeIndexResponse;
import com.smartrecruit.dto.ai.AiRetrievalSearchResponse;
import com.smartrecruit.dto.ai.AiRetrievedChunkResponse;
import com.smartrecruit.dto.request.KnowledgeDocumentCreateRequest;
import com.smartrecruit.dto.request.KnowledgeDocumentStatusRequest;
import com.smartrecruit.dto.request.KnowledgeDocumentUpdateRequest;
import com.smartrecruit.dto.response.BulkKnowledgeReindexResponse;
import com.smartrecruit.entity.IndexingStatus;
import com.smartrecruit.entity.KnowledgeCategory;
import com.smartrecruit.entity.KnowledgeDocument;
import com.smartrecruit.integration.ai.AiServiceClient;
import com.smartrecruit.integration.ai.AiServiceException;
import com.smartrecruit.repository.KnowledgeDocumentRepository;
import com.smartrecruit.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.ai-service.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiIndexingIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_URL = "/api/admin/hr/knowledge-documents";
    private static final String RETRIEVAL_URL = "/api/admin/hr/retrieval/search";

    @Autowired
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Autowired
    private MessageRepository messageRepository;

    @MockBean
    private AiServiceClient aiServiceClient;

    @BeforeEach
    void setUpAiService() {
        when(aiServiceClient.indexDocument(any())).thenAnswer(invocation -> {
            AiKnowledgeIndexRequest request = invocation.getArgument(0);
            return new AiKnowledgeIndexResponse(
                    request.documentId(), request.version(), "INDEXED",
                    2, 0, "fake-local-model", "2026-10-24T10:00:00Z");
        });
        when(aiServiceClient.deleteDocument(anyLong())).thenAnswer(invocation ->
                new AiKnowledgeDeleteResponse(invocation.getArgument(0), 2, "DELETED"));
    }

    @Test
    @DisplayName("Création active : indexation FastAPI puis statut INDEXED")
    void activeCreation_isIndexed() throws Exception {
        long id = createDocument("Document indexé", KnowledgeCategory.FAQ);

        KnowledgeDocument document = knowledgeDocumentRepository.findById(id).orElseThrow();
        assertThat(document.getIndexingStatus()).isEqualTo(IndexingStatus.INDEXED);
        assertThat(document.getIndexedAt()).isNotNull();
        assertThat(document.getIndexingError()).isNull();
        verify(aiServiceClient).indexDocument(any(AiKnowledgeIndexRequest.class));

        mockMvc.perform(get(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexingStatus").value("INDEXED"))
                .andExpect(jsonPath("$.indexedAt").isNotEmpty())
                .andExpect(jsonPath("$.indexingError").doesNotExist());
    }

    @Test
    @DisplayName("Échec FastAPI : document conservé et statut FAILED")
    void indexingFailure_keepsDocumentAndMarksFailed() throws Exception {
        doThrow(new AiServiceException("Le service d'indexation est indisponible"))
                .when(aiServiceClient).indexDocument(any());

        long id = createDocument("Document en échec", KnowledgeCategory.HR_POLICY);

        KnowledgeDocument document = knowledgeDocumentRepository.findById(id).orElseThrow();
        assertThat(document.getIndexingStatus()).isEqualTo(IndexingStatus.FAILED);
        assertThat(document.getIndexingError()).contains("indisponible");
        assertThat(document.getIndexedAt()).isNull();

        mockMvc.perform(get(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexingStatus").value("FAILED"))
                .andExpect(jsonPath("$.indexingError").value("Le service d'indexation est indisponible"));
    }

    @Test
    @DisplayName("Réponse FastAPI invalide : statut FAILED sans suppression MariaDB")
    void invalidAiResponse_marksFailed() throws Exception {
        doReturn(new AiKnowledgeIndexResponse(1L, 0, "INDEXED", 0, 0, null, null))
                .when(aiServiceClient).indexDocument(any());

        long id = createDocument("Réponse invalide", KnowledgeCategory.FAQ);

        assertThat(knowledgeDocumentRepository.existsById(id)).isTrue();
        assertThat(knowledgeDocumentRepository.findById(id).orElseThrow().getIndexingStatus())
                .isEqualTo(IndexingStatus.FAILED);
    }

    @Test
    @DisplayName("Modification active : nouvelle indexation complète")
    void updateActiveDocument_reindexes() throws Exception {
        long id = createDocument("Titre initial", KnowledgeCategory.FAQ);
        clearInvocations(aiServiceClient);

        mockMvc.perform(put(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeDocumentUpdateRequest(
                                "Titre modifié",
                                "Nouveau contenu avec un entretien de recrutement.",
                                KnowledgeCategory.INTERVIEW_GUIDE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexingStatus").value("INDEXED"));

        verify(aiServiceClient, times(1)).indexDocument(any(AiKnowledgeIndexRequest.class));
    }

    @Test
    @DisplayName("Désactivation : suppression vectorielle et statut NOT_INDEXED")
    void deactivation_removesVectors() throws Exception {
        long id = createDocument("Document à désactiver", KnowledgeCategory.OTHER);
        clearInvocations(aiServiceClient);

        mockMvc.perform(put(ADMIN_URL + "/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KnowledgeDocumentStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.indexingStatus").value("NOT_INDEXED"));

        verify(aiServiceClient).deleteDocument(id);
        assertThat(knowledgeDocumentRepository.findById(id).orElseThrow().getIndexedAt()).isNull();
    }

    @Test
    @DisplayName("Réactivation : indexation puis INDEXED")
    void reactivation_reindexes() throws Exception {
        long id = createDocument("Document réactivé", KnowledgeCategory.OTHER);
        setStatus(id, false);
        clearInvocations(aiServiceClient);

        mockMvc.perform(put(ADMIN_URL + "/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KnowledgeDocumentStatusRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.indexingStatus").value("INDEXED"));

        verify(aiServiceClient).indexDocument(any(AiKnowledgeIndexRequest.class));
    }

    @Test
    @DisplayName("Réindexation manuelle : FAILED devient INDEXED")
    void manualReindex_recoversFailedDocument() throws Exception {
        doThrow(new AiServiceException("Le service d'indexation est indisponible"))
                .when(aiServiceClient).indexDocument(any());
        long id = createDocument("Document à réparer", KnowledgeCategory.FAQ);
        assertThat(knowledgeDocumentRepository.findById(id).orElseThrow().getIndexingStatus())
                .isEqualTo(IndexingStatus.FAILED);

        doAnswer(invocation -> {
            AiKnowledgeIndexRequest request = invocation.getArgument(0);
            return new AiKnowledgeIndexResponse(
                    request.documentId(), request.version(), "INDEXED",
                    2, 0, "fake-local-model", "2026-10-24T10:00:00Z");
        }).when(aiServiceClient).indexDocument(any());
        clearInvocations(aiServiceClient);

        mockMvc.perform(post(ADMIN_URL + "/" + id + "/reindex")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexingStatus").value("INDEXED"))
                .andExpect(jsonPath("$.indexingError").doesNotExist());

        verify(aiServiceClient).indexDocument(any(AiKnowledgeIndexRequest.class));
    }

    @Test
    @DisplayName("Réindexation manuelle d'un document inactif : NOT_INDEXED")
    void manualReindexOnInactiveDocument_removesVectors() throws Exception {
        long id = createDocument("Document inactif", KnowledgeCategory.FAQ);
        setStatus(id, false);
        clearInvocations(aiServiceClient);

        mockMvc.perform(post(ADMIN_URL + "/" + id + "/reindex")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexingStatus").value("NOT_INDEXED"));

        verify(aiServiceClient).deleteDocument(id);
    }

    @Test
    @DisplayName("Réindexation globale : seuls les documents actifs sont traités")
    void reindexAll_processesActiveDocuments() throws Exception {
        createDocument("Actif 1", KnowledgeCategory.FAQ);
        createDocument("Actif 2", KnowledgeCategory.OTHER);
        long inactiveId = createDocument("Inactif", KnowledgeCategory.PLATFORM_HELP);
        setStatus(inactiveId, false);
        clearInvocations(aiServiceClient);

        String response = mockMvc.perform(post(ADMIN_URL + "/reindex-all")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.indexed").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andReturn().getResponse().getContentAsString();

        BulkKnowledgeReindexResponse result =
                objectMapper.readValue(response, BulkKnowledgeReindexResponse.class);
        assertThat(result.items()).hasSize(2);
        verify(aiServiceClient, times(2)).indexDocument(any(AiKnowledgeIndexRequest.class));
    }

    @Test
    @DisplayName("Recherche ADMIN : chunks retournés, aucun message ASSISTANT créé")
    void adminSearch_returnsChunksWithoutAssistantMessage() throws Exception {
        when(aiServiceClient.search(any())).thenReturn(new AiRetrievalSearchResponse(
                "Comment recruter ?",
                "Comment recruter ?",
                List.of(new AiRetrievedChunkResponse(
                        12L, "document-12-v1-chunk-0", 0, "Processus de recrutement",
                        KnowledgeCategory.RECRUITMENT_PROCEDURE,
                        "Le processus comporte un entretien.", 0.87, 1L)),
                1,
                "fake-local-model",
                12L
        ));

        mockMvc.perform(post(RETRIEVAL_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Comment recruter ?\",\"topK\":5,"
                                + "\"categories\":[\"RECRUITMENT_PROCEDURE\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(1))
                .andExpect(jsonPath("$.results[0].documentId").value(12))
                .andExpect(jsonPath("$.results[0].score").value(0.87))
                .andExpect(jsonPath("$.results[0].content").value("Le processus comporte un entretien."))
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andExpect(jsonPath("$.generatedAnswer").doesNotExist());

        assertThat(messageRepository.count()).isZero();
    }

    @Test
    @DisplayName("Réindexation et recherche refusées aux rôles non ADMIN")
    void nonAdmin_cannotReindexOrSearch() throws Exception {
        String candidateToken = createCandidateAndGetToken("ai.indexing.candidate@test.local");
        long id = createDocument("Document protégé", KnowledgeCategory.FAQ);

        mockMvc.perform(post(ADMIN_URL + "/" + id + "/reindex")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ADMIN_URL + "/reindex-all")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(RETRIEVAL_URL)
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"recrutement\",\"topK\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Suppression : le document MariaDB disparaît même si FastAPI est indisponible")
    void delete_remainsAvailableWhenAiServiceFails() throws Exception {
        long id = createDocument("Document à supprimer", KnowledgeCategory.FAQ);
        doThrow(new AiServiceException("La suppression vectorielle est indisponible"))
                .when(aiServiceClient).deleteDocument(anyLong());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(ADMIN_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        assertThat(knowledgeDocumentRepository.existsById(id)).isFalse();
    }

    @Test
    @DisplayName("Recherche : validation topK et question obligatoire")
    void searchValidation() throws Exception {
        String token = adminToken();

        mockMvc.perform(post(RETRIEVAL_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"  \",\"topK\":5}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(RETRIEVAL_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"recrutement\",\"topK\":99}"))
                .andExpect(status().isBadRequest());
    }

    private long createDocument(String title, KnowledgeCategory category) throws Exception {
        String response = mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeDocumentCreateRequest(
                                title,
                                "Contenu de test pour " + title + " avec un entretien.",
                                category))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    private void setStatus(long id, boolean active) throws Exception {
        mockMvc.perform(put(ADMIN_URL + "/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KnowledgeDocumentStatusRequest(active))))
                .andExpect(status().isOk());
    }
}
