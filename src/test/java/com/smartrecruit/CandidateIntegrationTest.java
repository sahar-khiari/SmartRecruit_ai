package com.smartrecruit;

import com.smartrecruit.dto.request.AdminCandidateCreateRequest;
import com.smartrecruit.dto.request.CandidateProfileUpdateRequest;
import com.smartrecruit.dto.request.CandidateSkillsUpdateRequest;
import com.smartrecruit.dto.request.UpdateUserRoleRequest;
import com.smartrecruit.entity.ExperienceLevel;
import com.smartrecruit.entity.RoleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CandidateIntegrationTest extends AbstractIntegrationTest {

    private static final CandidateProfileUpdateRequest PROFILE = new CandidateProfileUpdateRequest(
            "Développeur Full Stack", "+216 22 333 444", "Tunis",
            "https://github.com/ahmed", "https://www.linkedin.com/in/ahmed",
            "Développeur Java et Angular.", null, 4);

    @Test
    @DisplayName("GET /api/candidates/me retourne un profil vide avant le premier enregistrement")
    void getMyProfile_beforeFirstSave_returnsEmptyProfile() throws Exception {
        String token = createCandidateAndGetToken("vide@test.local");

        mockMvc.perform(get("/api/candidates/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("vide@test.local"))
                .andExpect(jsonPath("$.profileCompleted").value(false))
                .andExpect(jsonPath("$.skills", hasSize(0)));
    }

    @Test
    @DisplayName("PUT /api/candidates/me enregistre le profil et déduit le niveau des années")
    void updateMyProfile_derivesLevelFromYears() throws Exception {
        String token = createCandidateAndGetToken("profil@test.local");

        mockMvc.perform(put("/api/candidates/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PROFILE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCompleted").value(true))
                .andExpect(jsonPath("$.headline").value("Développeur Full Stack"))
                .andExpect(jsonPath("$.yearsOfExperience").value(4))
                .andExpect(jsonPath("$.experienceLevel").value("INTERMEDIATE"));
    }

    @Test
    @DisplayName("PUT /api/candidates/me refuse un lien GitHub invalide (400)")
    void updateMyProfile_invalidUrl_returns400() throws Exception {
        String token = createCandidateAndGetToken("url@test.local");
        CandidateProfileUpdateRequest invalid = new CandidateProfileUpdateRequest(
                null, null, null, "github.com/sans-protocole", null, null, null, null);

        mockMvc.perform(put("/api/candidates/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.githubUrl").exists());
    }

    @Test
    @DisplayName("PUT /api/candidates/me/skills remplace la liste et ignore les doublons")
    void updateMySkills_replacesAndDeduplicates() throws Exception {
        String token = createCandidateAndGetToken("skills@test.local");

        putSkills(token, "/api/candidates/me/skills", List.of("Java", "java ", "Docker", "Angular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", hasSize(3)))
                .andExpect(jsonPath("$.skills[0].name").value("Angular"))
                .andExpect(jsonPath("$.skills[0].source").value("MANUAL"));

        putSkills(token, "/api/candidates/me/skills", List.of("JAVA", "Spring Boot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", hasSize(2)))
                .andExpect(jsonPath("$.skills[0].name").value("JAVA"))
                .andExpect(jsonPath("$.skills[1].name").value("Spring Boot"));
    }

    @Test
    @DisplayName("Un recruteur ne peut pas utiliser /api/candidates/me (403)")
    void recruiter_cannotAccessCandidateMe() throws Exception {
        String token = createRecruiterAndGetToken("recruteur.me@test.local");

        mockMvc.perform(get("/api/candidates/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un candidat ne peut pas lister les autres candidats (403)")
    void candidate_cannotSearchCandidates() throws Exception {
        String token = createCandidateAndGetToken("curieux@test.local");

        mockMvc.perform(get("/api/candidates").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un recruteur recherche par compétence et par niveau, puis consulte le détail")
    void recruiter_searchesBySkillAndLevel() throws Exception {
        String candidateToken = createCandidateAndGetToken("cherche@test.local");
        mockMvc.perform(put("/api/candidates/me")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PROFILE)))
                .andExpect(status().isOk());
        putSkills(candidateToken, "/api/candidates/me/skills", List.of("Kubernetes")).andExpect(status().isOk());
        createCandidateAndGetToken("sans.profil@test.local");

        String recruiterToken = createRecruiterAndGetToken("recruteur@test.local");

        mockMvc.perform(get("/api/candidates")
                        .param("search", "kuber")
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("cherche@test.local"))
                .andExpect(jsonPath("$.content[0].skills[0]").value("Kubernetes"));

        mockMvc.perform(get("/api/candidates")
                        .param("level", ExperienceLevel.SENIOR.name())
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        Long id = userRepository.findByEmail("cherche@test.local").orElseThrow().getId();
        mockMvc.perform(get("/api/candidates/{id}", id).header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Tunis"));
    }

    @Test
    @DisplayName("GET /api/candidates/{id} renvoie 404 pour un compte qui n'est pas candidat")
    void getCandidate_notCandidate_returns404() throws Exception {
        Long adminId = userRepository.findByEmail(adminEmail).orElseThrow().getId();

        mockMvc.perform(get("/api/candidates/{id}", adminId).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("L'admin crée un candidat qui peut ensuite se connecter")
    void admin_createsCandidate() throws Exception {
        AdminCandidateCreateRequest request = new AdminCandidateCreateRequest(
                "Ahmed", "Ben Salah", "Ahmed.BS@Test.local", DEFAULT_PASSWORD, PROFILE);

        mockMvc.perform(post("/api/admin/candidates")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ahmed.bs@test.local"))
                .andExpect(jsonPath("$.profileCompleted").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());

        loginAndGetToken("ahmed.bs@test.local", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/admin/candidates")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Un recruteur ne peut pas utiliser /api/admin/candidates (403)")
    void recruiter_cannotUseAdminEndpoints() throws Exception {
        String token = createRecruiterAndGetToken("recruteur.admin@test.local");

        mockMvc.perform(delete("/api/admin/candidates/{id}", 1L).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("L'admin modifie le profil et les compétences, puis supprime le candidat")
    void admin_updatesAndDeletesCandidate() throws Exception {
        createCandidateAndGetToken("gere@test.local");
        Long id = userRepository.findByEmail("gere@test.local").orElseThrow().getId();
        String admin = adminToken();

        mockMvc.perform(put("/api/admin/candidates/{id}", id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PROFILE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceLevel").value("INTERMEDIATE"));

        putSkills(admin, "/api/admin/candidates/" + id + "/skills", List.of("Python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0].name").value("Python"));

        mockMvc.perform(delete("/api/admin/candidates/{id}", id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/candidates/{id}", id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("L'admin ne peut pas supprimer un compte non candidat via ce module (404)")
    void admin_cannotDeleteNonCandidate() throws Exception {
        Long adminId = userRepository.findByEmail(adminEmail).orElseThrow().getId();

        mockMvc.perform(delete("/api/admin/candidates/{id}", adminId).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions putSkills(String token, String url,
                                                                          List<String> skills) throws Exception {
        return mockMvc.perform(put(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CandidateSkillsUpdateRequest(skills))));
    }

    private String createRecruiterAndGetToken(String email) throws Exception {
        performSignup("Recruteur", "Test", email, DEFAULT_PASSWORD).andExpect(status().isCreated());
        Long id = userRepository.findByEmail(email).orElseThrow().getId();

        mockMvc.perform(put("/api/admin/users/{id}/role", id)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(RoleName.RECRUITER))))
                .andExpect(status().isOk());

        return loginAndGetToken(email, DEFAULT_PASSWORD);
    }
}
