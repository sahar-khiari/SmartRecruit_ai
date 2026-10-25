package com.smartrecruit;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartrecruit.dto.ai.AiRagChatResponse;
import com.smartrecruit.dto.ai.AiRagSourceResponse;
import com.smartrecruit.dto.request.ConversationCreateRequest;
import com.smartrecruit.dto.request.UpdateUserRoleRequest;
import com.smartrecruit.entity.AiRun;
import com.smartrecruit.entity.AiRunStatus;
import com.smartrecruit.entity.KnowledgeCategory;
import com.smartrecruit.entity.MessageRole;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.integration.ai.AiServiceClient;
import com.smartrecruit.integration.ai.AiServiceException;
import com.smartrecruit.repository.AiRunRepository;
import com.smartrecruit.repository.ConversationRepository;
import com.smartrecruit.repository.MessageRepository;
import com.smartrecruit.repository.MessageSourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class HrAssistantIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageSourceRepository messageSourceRepository;

    @Autowired
    private AiRunRepository aiRunRepository;

    @MockBean
    private AiServiceClient aiServiceClient;

    @Test
    @DisplayName("Question CANDIDATE : réponse ASSISTANT, source vérifiée et AiRun COMPLETED")
    void ask_candidate_persistsGroundedAnswerAndSource() throws Exception {
        String token = createCandidateAndGetToken("hr.ask.success@test.local");
        long conversationId = createConversation(token, "Question RAG");
        when(aiServiceClient.generateRagAnswer(any())).thenReturn(groundedResponse());

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Comment postuler à une offre ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.userMessage.content").value("Comment postuler à une offre ?"))
                .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.assistantMessage.content")
                        .value("Consultez les offres depuis votre espace candidat."))
                .andExpect(jsonPath("$.assistantMessage.sources[0].chunkId")
                        .value("document-10-v1-chunk-0"))
                .andExpect(jsonPath("$.sources[0].documentId").value(10))
                .andExpect(jsonPath("$.sources[0].score").value(0.74))
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.needsHumanReview").value(false))
                .andExpect(jsonPath("$.modelName").value("qwen3:4b"))
                .andExpect(jsonPath("$.promptVersion").value("hr-rag-v1"));

        assertThat(messageRepository.countByConversation_Id(conversationId)).isEqualTo(2);
        assertThat(messageSourceRepository.countByMessage_Id(
                messageRepository.findAllByConversation_IdOrderByCreatedAtAscIdAsc(conversationId)
                        .stream()
                        .filter(message -> message.getRole() == MessageRole.ASSISTANT)
                        .findFirst()
                        .orElseThrow()
                        .getId()
        )).isEqualTo(1);

        AiRun run = aiRunRepository.findAllByConversationIdOrderByIdAsc(conversationId).get(0);
        assertThat(run.getStatus()).isEqualTo(AiRunStatus.COMPLETED);
        assertThat(run.getAssistantMessageId()).isNotNull();
        assertThat(run.getGrounded()).isTrue();
        assertThat(run.getRetrievedChunkCount()).isEqualTo(1);
        assertThat(run.getErrorCode()).isNull();
    }

    @Test
    @DisplayName("Contexte insuffisant : repli contrôlé sans source et AiRun COMPLETED non fondé")
    void ask_fallback_isPersistedWithoutSource() throws Exception {
        String token = createCandidateAndGetToken("hr.ask.fallback@test.local");
        long conversationId = createConversation(token, "Hors domaine");
        when(aiServiceClient.generateRagAnswer(any())).thenReturn(fallbackResponse());

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Quel temps fera-t-il demain ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.assistantMessage.sources").isEmpty())
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.needsHumanReview").value(true));

        assertThat(messageRepository.countByConversation_Id(conversationId)).isEqualTo(2);
        AiRun run = aiRunRepository.findAllByConversationIdOrderByIdAsc(conversationId).get(0);
        assertThat(run.getStatus()).isEqualTo(AiRunStatus.COMPLETED);
        assertThat(run.getGrounded()).isFalse();
        assertThat(run.getNeedsHumanReview()).isTrue();
        assertThat(run.getRetrievedChunkCount()).isZero();
    }

    @Test
    @DisplayName("Sortie IA invalide : 502, USER conservé, aucun ASSISTANT, AiRun FAILED")
    void ask_invalidSource_returns502WithoutAssistantMessage() throws Exception {
        String token = createCandidateAndGetToken("hr.ask.invalid@test.local");
        long conversationId = createConversation(token, "Source invalide");
        when(aiServiceClient.generateRagAnswer(any())).thenReturn(
                new AiRagChatResponse(
                        "Réponse inventée.",
                        KnowledgeCategory.PLATFORM_HELP,
                        true,
                        false,
                        List.of(new AiRagSourceResponse(
                                null, "document-999-v1-chunk-0", 0,
                                "Source inventée", KnowledgeCategory.PLATFORM_HELP,
                                0.99, 1L, "extrait"
                        )),
                        "question", "qwen3:4b", "hr-rag-v1",
                        10, 20, 30, 100, 50
                )
        );

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Comment postuler ?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("La réponse du modèle local est invalide."));

        assertThat(messageRepository.countByConversation_Id(conversationId)).isEqualTo(1);
        AiRun run = aiRunRepository.findAllByConversationIdOrderByIdAsc(conversationId).get(0);
        assertThat(run.getStatus()).isEqualTo(AiRunStatus.FAILED);
        assertThat(run.getErrorCode()).isEqualTo("INVALID_AI_RESPONSE");
    }

    @Test
    @DisplayName("Ollama indisponible : 503, USER conservé, aucun ASSISTANT, AiRun FAILED")
    void ask_ollamaUnavailable_returns503AndKeepsUserMessage() throws Exception {
        String token = createCandidateAndGetToken("hr.ask.ollama@test.local");
        long conversationId = createConversation(token, "Ollama indisponible");
        when(aiServiceClient.generateRagAnswer(any())).thenThrow(
                new AiServiceException("indisponible", "OLLAMA_UNAVAILABLE", null)
        );

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Comment postuler ?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("Le modèle local n'est pas disponible. "
                                + "Votre message a été enregistré. Vous pouvez réessayer."));

        assertThat(messageRepository.countByConversation_Id(conversationId)).isEqualTo(1);
        AiRun run = aiRunRepository.findAllByConversationIdOrderByIdAsc(conversationId).get(0);
        assertThat(run.getStatus()).isEqualTo(AiRunStatus.FAILED);
        assertThat(run.getErrorCode()).isEqualTo("OLLAMA_UNAVAILABLE");
        assertThat(run.getAssistantMessageId()).isNull();
    }

    @Test
    @DisplayName("Délai Ollama dépassé : 504 et AiRun FAILED")
    void ask_generationTimeout_returns504() throws Exception {
        String token = createCandidateAndGetToken("hr.ask.timeout@test.local");
        long conversationId = createConversation(token, "Timeout");
        when(aiServiceClient.generateRagAnswer(any())).thenThrow(
                new AiServiceException("timeout", "GENERATION_TIMEOUT", null)
        );

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Comment postuler ?\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504));

        assertThat(messageRepository.countByConversation_Id(conversationId)).isEqualTo(1);
        AiRun run = aiRunRepository.findAllByConversationIdOrderByIdAsc(conversationId).get(0);
        assertThat(run.getStatus()).isEqualTo(AiRunStatus.FAILED);
        assertThat(run.getErrorCode()).isEqualTo("GENERATION_TIMEOUT");
    }

    @Test
    @DisplayName("Conversation d'un autre utilisateur : 404 et aucun AiRun créé")
    void ask_otherUserConversation_returns404() throws Exception {
        String ownerToken = createCandidateAndGetToken("hr.ask.owner@test.local");
        String otherToken = createCandidateAndGetToken("hr.ask.other@test.local");
        long conversationId = createConversation(ownerToken, "Privée");

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Intrusion\"}"))
                .andExpect(status().isNotFound());

        assertThat(aiRunRepository.findAllByConversationIdOrderByIdAsc(conversationId)).isEmpty();
        assertThat(messageRepository.countByConversation_Id(conversationId)).isZero();
    }

    @Test
    @DisplayName("Le rôle client est ignoré : le message reste USER")
    void ask_clientRoleIsIgnored() throws Exception {
        String token = createCandidateAndGetToken("hr.ask.role@test.local");
        long conversationId = createConversation(token, "Rôle client");
        when(aiServiceClient.generateRagAnswer(any())).thenReturn(fallbackResponse());

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Comment postuler ?\",\"role\":\"SYSTEM\","
                                + "\"userId\":999,\"modelName\":\"cloud-model\","
                                + "\"sources\":[{\"documentId\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.modelName").value("qwen3:4b"));

        assertThat(messageRepository.findAllByConversation_IdOrderByCreatedAtAscIdAsc(conversationId))
                .first()
                .satisfies(message -> assertThat(message.getRole()).isEqualTo(MessageRole.USER));
        verify(aiServiceClient).generateRagAnswer(any());
    }

    @Test
    @DisplayName("RECRUITER : peut poser une question via /ask")
    void ask_recruiter_isAllowed() throws Exception {
        String recruiterToken = createRecruiterAndGetToken("hr.ask.recruiter@test.local");
        long conversationId = createConversation(recruiterToken, "Question recruteur");
        when(aiServiceClient.generateRagAnswer(any())).thenReturn(fallbackResponse());

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Comment fonctionne le suivi ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"));
    }

    @Test
    @DisplayName("Question vide : 400, aucun message ni AiRun")
    void ask_blankContent_returns400() throws Exception {
        String token = createCandidateAndGetToken("hr.ask.blank@test.local");
        long conversationId = createConversation(token, "Validation");

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.content").exists());

        assertThat(messageRepository.countByConversation_Id(conversationId)).isZero();
        assertThat(aiRunRepository.findAllByConversationIdOrderByIdAsc(conversationId)).isEmpty();
    }

    @Test
    @DisplayName("Historique : les sources du message ASSISTANT sont renvoyées")
    void listMessages_includesAssistantSources() throws Exception {
        String token = createCandidateAndGetToken("hr.ask.history@test.local");
        long conversationId = createConversation(token, "Historique");
        when(aiServiceClient.generateRagAnswer(any())).thenReturn(groundedResponse());

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Comment postuler ?\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/hr/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].sources").isEmpty())
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].sources[0].title").value("Comment postuler à une offre"))
                .andExpect(jsonPath("$[1].sources[0].score").value(0.74));
    }

    private AiRagChatResponse groundedResponse() {
        return new AiRagChatResponse(
                "Consultez les offres depuis votre espace candidat.",
                KnowledgeCategory.PLATFORM_HELP,
                true,
                false,
                List.of(new AiRagSourceResponse(
                        10L,
                        "document-10-v1-chunk-0",
                        0,
                        "Comment postuler à une offre",
                        KnowledgeCategory.PLATFORM_HELP,
                        0.74,
                        1L,
                        "Pour postuler, consultez les offres…"
                )),
                "Comment postuler à une offre ?",
                "qwen3:4b",
                "hr-rag-v1",
                12,
                345,
                357,
                420,
                38
        );
    }

    private AiRagChatResponse fallbackResponse() {
        return new AiRagChatResponse(
                "Je ne dispose pas d'informations suffisantes dans la base de connaissances RH "
                        + "pour répondre à cette question.",
                KnowledgeCategory.OTHER,
                false,
                true,
                List.of(),
                "Quel temps fera-t-il demain ?",
                "qwen3:4b",
                "hr-rag-v1",
                8,
                0,
                8,
                null,
                null
        );
    }

    private long createConversation(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/hr/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConversationCreateRequest(title))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RECRUITER"));

        return loginAndGetToken(email, DEFAULT_PASSWORD);
    }
}
