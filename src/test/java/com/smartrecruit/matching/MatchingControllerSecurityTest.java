package com.smartrecruit.matching;

import com.smartrecruit.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de sécurité des endpoints du module matching.
 * Vérifie que les endpoints protégés renvoient 401 sans JWT, et que /status est accessible.
 */
@DisplayName("MatchingController — Sécurité des endpoints")
class MatchingControllerSecurityTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /api/matching/status — accessible avec authentification ou refus sans token")
    void statusEndpointSecurityCheck() throws Exception {
        // En fonction de SecurityConfig, anyRequest() requiert authentification sauf whitelist
        // Sans token, renvoie 401
        mockMvc.perform(get("/api/matching/status"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/matching/profile — 401 sans JWT")
    void profileRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/matching/profile"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/matching/offers/1/run — 401 sans JWT")
    void runMatchingRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/matching/offers/1/run"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/matching/candidates/me/recommendations — 401 sans JWT")
    void recommendationsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/matching/candidates/me/recommendations"))
               .andExpect(status().isUnauthorized());
    }
}
