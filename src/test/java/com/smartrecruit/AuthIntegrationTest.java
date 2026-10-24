package com.smartrecruit;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Inscription : crée un CANDIDATE avec mot de passe BCrypt et normalise l'email")
    void signup_success() throws Exception {
        performSignup("Sahar", "Khiari", "Sahar.Signup@Test.Local", DEFAULT_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Utilisateur créé avec succès"));

        User user = userRepository.findByEmail("sahar.signup@test.local").orElseThrow();
        assertThat(user.getRole().getName()).isEqualTo(RoleName.CANDIDATE);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getPassword()).isNotEqualTo(DEFAULT_PASSWORD);
        assertThat(user.getPassword()).startsWith("$2");
    }

    @Test
    @DisplayName("Inscription : refuse un email déjà utilisé (409)")
    void signup_duplicateEmail_returns409() throws Exception {
        performSignup("Sahar", "Khiari", "duplicate@test.local", DEFAULT_PASSWORD)
                .andExpect(status().isCreated());

        performSignup("Autre", "Personne", "DUPLICATE@test.local", DEFAULT_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("existe déjà")));
    }

    @Test
    @DisplayName("Inscription : refuse un mot de passe faible (400 + erreurs de validation)")
    void signup_weakPassword_returns400() throws Exception {
        performSignup("Sahar", "Khiari", "weak@test.local", "faible")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    @DisplayName("Login : retourne un JWT et l'utilisateur sans mot de passe")
    void login_success() throws Exception {
        performSignup("Sahar", "Khiari", "login@test.local", DEFAULT_PASSWORD)
                .andExpect(status().isCreated());

        String response = performLogin("LOGIN@test.local", DEFAULT_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.email").value("login@test.local"))
                .andExpect(jsonPath("$.user.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.user.enabled").value(true))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("accessToken").asText().split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Login : refuse un mauvais mot de passe (401)")
    void login_wrongPassword_returns401() throws Exception {
        performSignup("Sahar", "Khiari", "wrongpass@test.local", DEFAULT_PASSWORD)
                .andExpect(status().isCreated());

        performLogin("wrongpass@test.local", "MauvaisPass1!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
    }

    @Test
    @DisplayName("Login : refuse un compte désactivé (403)")
    void login_disabledAccount_returns403() throws Exception {
        performSignup("Compte", "Desactive", "disabled@test.local", DEFAULT_PASSWORD)
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("disabled@test.local").orElseThrow();
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        performLogin("disabled@test.local", DEFAULT_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("désactivé")));
    }

    @Test
    @DisplayName("Endpoint protégé sans JWT : 401 JSON")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/users/me"));
    }
}
