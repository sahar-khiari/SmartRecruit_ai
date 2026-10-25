package com.smartrecruit;

import com.smartrecruit.dto.request.ChangePasswordRequest;
import com.smartrecruit.dto.request.UpdateProfileRequest;
import com.smartrecruit.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UserProfileIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /api/users/me avec JWT retourne le profil sans mot de passe")
    void getProfile_withJwt_returnsProfile() throws Exception {
        String token = createCandidateAndGetToken("profile@test.local");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile@test.local"))
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/users/me modifie prénom, nom et email")
    void updateProfile_success() throws Exception {
        String token = createCandidateAndGetToken("update@test.local");

        UpdateProfileRequest request = new UpdateProfileRequest(
                "Nouveau", "Nom", "nouveau.email@test.local");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Nouveau"))
                .andExpect(jsonPath("$.lastName").value("Nom"))
                .andExpect(jsonPath("$.email").value("nouveau.email@test.local"));

        assertThat(userRepository.existsByEmail("nouveau.email@test.local")).isTrue();
    }

    @Test
    @DisplayName("PUT /api/users/me refuse un email déjà utilisé (409)")
    void updateProfile_emailAlreadyUsed_returns409() throws Exception {
        performSignup("Autre", "Utilisateur", "occupe@test.local", DEFAULT_PASSWORD);
        String token = createCandidateAndGetToken("change@test.local");

        UpdateProfileRequest request = new UpdateProfileRequest(
                "Change", "Moi", "occupe@test.local");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("PUT /api/users/me/password vérifie l'ancien mot de passe")
    void changePassword_flow() throws Exception {
        String token = createCandidateAndGetToken("password@test.local");

        ChangePasswordRequest wrongCurrent = new ChangePasswordRequest(
                "MauvaisActuel1!", "NouveauPassword1!");
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCurrent)))
                .andExpect(status().isUnauthorized());

        ChangePasswordRequest valid = new ChangePasswordRequest(
                DEFAULT_PASSWORD, "NouveauPassword1!");
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mot de passe modifié avec succès"));

        User user = userRepository.findByEmail("password@test.local").orElseThrow();
        assertThat(user.getPassword()).startsWith("$2");

        loginAndGetToken("password@test.local", "NouveauPassword1!");
    }
}
