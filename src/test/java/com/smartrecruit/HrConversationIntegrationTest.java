package com.smartrecruit;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartrecruit.dto.request.ConversationCreateRequest;
import com.smartrecruit.dto.request.ConversationUpdateRequest;
import com.smartrecruit.dto.request.MessageCreateRequest;
import com.smartrecruit.dto.request.UpdateUserRoleRequest;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.repository.ConversationRepository;
import com.smartrecruit.repository.MessageRepository;
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
class HrConversationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Test
    @DisplayName("Création : titre par défaut et propriétaire issu du JWT, sans userId client")
    void createConversation_usesDefaultTitleAndAuthenticatedOwner() throws Exception {
        String token = createCandidateAndGetToken("hr.create@test.local");

        mockMvc.perform(post("/api/hr/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"userId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Nouvelle conversation"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());

        assertThat(conversationRepository.findAll())
                .singleElement()
                .satisfies(conversation -> assertThat(conversation.getUser().getEmail())
                        .isEqualTo("hr.create@test.local"));
    }

    @Test
    @DisplayName("Création : un titre fourni est conservé")
    void createConversation_withTitle() throws Exception {
        String token = createCandidateAndGetToken("hr.create.title@test.local");

        mockMvc.perform(post("/api/hr/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConversationCreateRequest("Préparation entretien Java"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Préparation entretien Java"));
    }

    @Test
    @DisplayName("Liste : uniquement les conversations de l'utilisateur connecté")
    void listConversations_onlyOwnedByCurrentUser() throws Exception {
        String ownerToken = createCandidateAndGetToken("hr.owner@test.local");
        String otherToken = createCandidateAndGetToken("hr.other@test.local");

        createConversation(ownerToken, "Conversation A");
        createConversation(ownerToken, "Conversation B");
        createConversation(otherToken, "Conversation secrète");

        mockMvc.perform(get("/api/hr/conversations")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + ownerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].title", hasItem("Conversation A")))
                .andExpect(jsonPath("$.content[*].title", hasItem("Conversation B")))
                .andExpect(jsonPath("$.content[*].title", not(hasItem("Conversation secrète"))));
    }

    @Test
    @DisplayName("Consultation et renommage : sa conversation est accessible, celle d'un autre renvoie 404")
    void getAndRenameConversation_enforcesOwnership() throws Exception {
        String ownerToken = createCandidateAndGetToken("hr.rename.owner@test.local");
        String otherToken = createCandidateAndGetToken("hr.rename.other@test.local");
        long conversationId = createConversation(ownerToken, "Titre initial");

        mockMvc.perform(get("/api/hr/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titre initial"));

        mockMvc.perform(get("/api/hr/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + otherToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(put("/api/hr/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConversationUpdateRequest("Questions sur les entretiens"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Questions sur les entretiens"));

        mockMvc.perform(put("/api/hr/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConversationUpdateRequest("Tentative"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Suppression : sa conversation est supprimée, celle d'un autre renvoie 404")
    void deleteConversation_enforcesOwnership() throws Exception {
        String ownerToken = createCandidateAndGetToken("hr.delete.owner@test.local");
        String otherToken = createCandidateAndGetToken("hr.delete.other@test.local");
        long conversationId = createConversation(ownerToken, "À supprimer");

        mockMvc.perform(delete("/api/hr/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/hr/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(conversationRepository.existsById(conversationId)).isFalse();
    }

    @Test
    @DisplayName("Suppression d'une conversation : ses messages sont supprimés en cascade")
    void deleteConversation_cascadesMessages() throws Exception {
        String token = createCandidateAndGetToken("hr.cascade@test.local");
        long conversationId = createConversation(token, "Avec messages");

        createMessage(token, conversationId, "Premier message");
        createMessage(token, conversationId, "Deuxième message");
        assertThat(messageRepository.countByConversation_Id(conversationId)).isEqualTo(2);

        mockMvc.perform(delete("/api/hr/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(conversationRepository.existsById(conversationId)).isFalse();
        assertThat(messageRepository.countByConversation_Id(conversationId)).isZero();
    }

    @Test
    @DisplayName("Sans JWT : la liste et la création de conversations renvoient 401")
    void conversations_withoutJwt_return401() throws Exception {
        mockMvc.perform(get("/api/hr/conversations").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/hr/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConversationCreateRequest("Test"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Message : le rôle USER est imposé même si role=SYSTEM est envoyé par le client")
    void createMessage_forcesUserRole() throws Exception {
        String token = createCandidateAndGetToken("hr.message.role@test.local");
        long conversationId = createConversation(token, "Rôle forcé");

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Comment préparer un entretien ?\",\"role\":\"SYSTEM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.content").value("Comment préparer un entretien ?"))
                .andExpect(jsonPath("$.conversationId").value(conversationId));
    }

    @Test
    @DisplayName("Message : un contenu vide ou composé d'espaces est refusé (400)")
    void createMessage_blankContent_returns400() throws Exception {
        String token = createCandidateAndGetToken("hr.message.blank@test.local");
        long conversationId = createConversation(token, "Validation message");

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageCreateRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.content").exists());
    }

    @Test
    @DisplayName("Messages : retour dans l'ordre chronologique")
    void listMessages_returnsChronologicalOrder() throws Exception {
        String token = createCandidateAndGetToken("hr.message.order@test.local");
        long conversationId = createConversation(token, "Ordre");

        createMessage(token, conversationId, "Premier");
        createMessage(token, conversationId, "Deuxième");
        createMessage(token, conversationId, "Troisième");

        mockMvc.perform(get("/api/hr/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Premier"))
                .andExpect(jsonPath("$[1].content").value("Deuxième"))
                .andExpect(jsonPath("$[2].content").value("Troisième"))
                .andExpect(jsonPath("$[0].role").value("USER"));
    }

    @Test
    @DisplayName("Message : impossible d'ajouter ou lire les messages de la conversation d'un autre (404)")
    void messages_ofAnotherUserConversation_return404() throws Exception {
        String ownerToken = createCandidateAndGetToken("hr.msg.owner@test.local");
        String otherToken = createCandidateAndGetToken("hr.msg.other@test.local");
        long conversationId = createConversation(ownerToken, "Privée");
        createMessage(ownerToken, conversationId, "Message privé");

        mockMvc.perform(get("/api/hr/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageCreateRequest("Intrusion"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RECRUITER : peut utiliser ses propres conversations et messages")
    void recruiter_canUseOwnConversations() throws Exception {
        String recruiterToken = createRecruiterAndGetToken("hr.recruiter@test.local");
        long conversationId = createConversation(recruiterToken, "Conversation recruteur");

        createMessage(recruiterToken, conversationId, "Question de recrutement");

        mockMvc.perform(get("/api/hr/conversations")
                        .header("Authorization", "Bearer " + recruiterToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Conversation recruteur"));
    }

    @Test
    @DisplayName("ADMIN : peut utiliser ses propres conversations via les endpoints utilisateur")
    void admin_canUseOwnConversations() throws Exception {
        String token = adminToken();
        long conversationId = createConversation(token, "Conversation admin");

        mockMvc.perform(get("/api/hr/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Conversation admin"));
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

    private void createMessage(String token, long conversationId, String content) throws Exception {
        mockMvc.perform(post("/api/hr/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageCreateRequest(content))))
                .andExpect(status().isCreated());
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
