package com.smartrecruit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.dto.request.LoginRequest;
import com.smartrecruit.dto.request.SignupRequest;
import com.smartrecruit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base commune des tests d'intégration.
 * Profil "test" : base H2 en mémoire (aucune dépendance à XAMPP/MariaDB).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "Password123!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Value("${app.admin.email}")
    protected String adminEmail;

    @Value("${app.admin.password}")
    protected String adminPassword;

    protected ResultActions performSignup(String firstName, String lastName, String email,
                                           String password) throws Exception {
        SignupRequest request = new SignupRequest(firstName, lastName, email, password);
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    protected ResultActions performLogin(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    protected String createCandidateAndGetToken(String email) throws Exception {
        performSignup("Candidat", "Test", email, DEFAULT_PASSWORD)
                .andExpect(status().isCreated());
        return loginAndGetToken(email, DEFAULT_PASSWORD);
    }

    protected String loginAndGetToken(String email, String password) throws Exception {
        String response = performLogin(email, password)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    protected String adminToken() throws Exception {
        return loginAndGetToken(adminEmail, adminPassword);
    }
}
