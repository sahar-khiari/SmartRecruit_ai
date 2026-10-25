package com.smartrecruit;

import com.smartrecruit.dto.request.UpdateUserRoleRequest;
import com.smartrecruit.dto.request.UpdateUserStatusRequest;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminSecurityIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Un CANDIDATE ne peut pas accéder aux endpoints ADMIN (403)")
    void candidate_cannotAccessAdminEndpoints() throws Exception {
        String candidateToken = createCandidateAndGetToken("candidate.admin@test.local");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + candidateToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Accès refusé")));
    }

    @Test
    @DisplayName("Un ADMIN peut lister les utilisateurs avec pagination et tri")
    void admin_canListUsers() throws Exception {
        createCandidateAndGetToken("listed@test.local");
        String token = adminToken();

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "email,asc")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.content[0].email").isNotEmpty())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    @DisplayName("Un ADMIN peut consulter un utilisateur et reçoit 404 si introuvable")
    void admin_canGetUserById() throws Exception {
        createCandidateAndGetToken("byid@test.local");
        User user = userRepository.findByEmail("byid@test.local").orElseThrow();
        String token = adminToken();

        mockMvc.perform(get("/api/admin/users/" + user.getId())
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("byid@test.local"));

        mockMvc.perform(get("/api/admin/users/999999")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Un ADMIN peut désactiver un compte, qui ne peut alors plus se connecter")
    void admin_canDisableUser() throws Exception {
        createCandidateAndGetToken("disable.me@test.local");
        User user = userRepository.findByEmail("disable.me@test.local").orElseThrow();
        String token = adminToken();

        mockMvc.perform(put("/api/admin/users/" + user.getId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        performLogin("disable.me@test.local", DEFAULT_PASSWORD)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un ADMIN peut changer le rôle d'un utilisateur")
    void admin_canChangeRole() throws Exception {
        createCandidateAndGetToken("role.change@test.local");
        User user = userRepository.findByEmail("role.change@test.local").orElseThrow();
        String token = adminToken();

        mockMvc.perform(put("/api/admin/users/" + user.getId() + "/role")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(RoleName.RECRUITER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RECRUITER"));

        assertThat(userRepository.findByEmail("role.change@test.local").orElseThrow()
                .getRole().getName()).isEqualTo(RoleName.RECRUITER);
    }

    @Test
    @DisplayName("Un ADMIN ne peut pas supprimer son propre compte (400)")
    void admin_cannotDeleteSelf() throws Exception {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        String token = adminToken();

        mockMvc.perform(delete("/api/admin/users/" + admin.getId())
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("propre compte")));

        assertThat(userRepository.existsByEmail(adminEmail)).isTrue();
    }

    @Test
    @DisplayName("Un ADMIN peut supprimer un autre utilisateur")
    void admin_canDeleteOtherUser() throws Exception {
        createCandidateAndGetToken("delete.me@test.local");
        User user = userRepository.findByEmail("delete.me@test.local").orElseThrow();
        String token = adminToken();

        mockMvc.perform(delete("/api/admin/users/" + user.getId())
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Utilisateur supprimé avec succès"));

        assertThat(userRepository.findByEmail("delete.me@test.local")).isEmpty();
    }

    @Test
    @DisplayName("Les endpoints ADMIN sans JWT retournent 401")
    void adminEndpoints_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/users").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
