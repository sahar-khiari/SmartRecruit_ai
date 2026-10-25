package com.smartrecruit;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartrecruit.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module 2 : CRUD enrichi, cycle de vie, recherche multicritère et droits sur les offres.
 */
class OffreGestionIntegrationTest extends AbstractIntegrationTest {

    @Test
    void cycleDeVieCompletEtVisibiliteCandidat() throws Exception {
        String recruteur = recruteurToken("rec.cycle@test.local");
        String candidat = createCandidateAndGetToken("cand.cycle@test.local");

        // Création en brouillon : invisible pour les candidats.
        long id = creerOffre(recruteur, "Développeur Java Cycle", false);
        mockMvc.perform(get("/api/offres/" + id).header("Authorization", bearer(candidat)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/offres/recherche").param("q", "Java Cycle").header("Authorization", bearer(candidat)))
                .andExpect(jsonPath("$.totalElements").value(0));

        // Publication : visible et trouvable.
        mockMvc.perform(put("/api/offres/" + id + "/publier").header("Authorization", bearer(recruteur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PUBLIEE"))
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/offres/recherche").param("q", "java cycle").param("localisation", "tunis")
                        .header("Authorization", bearer(candidat)))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Clôture puis archivage ; une offre archivée ne se modifie plus.
        mockMvc.perform(put("/api/offres/" + id + "/cloturer").header("Authorization", bearer(recruteur)))
                .andExpect(jsonPath("$.statut").value("CLOTUREE"))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(put("/api/offres/" + id + "/archiver").header("Authorization", bearer(recruteur)))
                .andExpect(jsonPath("$.statut").value("ARCHIVEE"));
        mockMvc.perform(put("/api/offres/" + id).header("Authorization", bearer(recruteur))
                        .contentType(MediaType.APPLICATION_JSON).content(json(offre("Autre titre", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transitionInterditeRefusee() throws Exception {
        String recruteur = recruteurToken("rec.transition@test.local");
        long id = creerOffre(recruteur, "Offre brouillon", false);
        mockMvc.perform(put("/api/offres/" + id + "/cloturer").header("Authorization", bearer(recruteur)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seulAuteurPeutModifier() throws Exception {
        String auteur = recruteurToken("rec.auteur@test.local");
        String autre = recruteurToken("rec.autre@test.local");
        long id = creerOffre(auteur, "Offre de l'auteur", true);

        mockMvc.perform(put("/api/offres/" + id).header("Authorization", bearer(autre))
                        .contentType(MediaType.APPLICATION_JSON).content(json(offre("Piratage", null))))
                .andExpect(status().isForbidden());
        // L'admin ne modifie pas le contenu (rôle RECRUITER exigé).
        mockMvc.perform(put("/api/offres/" + id).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(offre("Modif admin", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidatNePeutPasCreer() throws Exception {
        String candidat = createCandidateAndGetToken("cand.create@test.local");
        mockMvc.perform(post("/api/offres").header("Authorization", bearer(candidat))
                        .contentType(MediaType.APPLICATION_JSON).content(json(offre("Test", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void offreClotureeInvisibleEtAdminPeutSupprimer() throws Exception {
        String recruteur = recruteurToken("rec.admin@test.local");
        String candidat = createCandidateAndGetToken("cand.admin@test.local");
        long id = creerOffre(recruteur, "Offre clôturée", true);

        mockMvc.perform(put("/api/offres/" + id + "/cloturer").header("Authorization", bearer(recruteur)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/offres/" + id).header("Authorization", bearer(candidat)))
                .andExpect(status().isNotFound());
        // L'auteur la voit toujours.
        mockMvc.perform(get("/api/offres/" + id).header("Authorization", bearer(recruteur)))
                .andExpect(jsonPath("$.statut").value("CLOTUREE"));

        mockMvc.perform(delete("/api/offres/" + id).header("Authorization", bearer(adminToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void iaIndisponibleRenvoie503Propre() throws Exception {
        // app.ai-service.enabled=false dans le profil de test.
        String recruteur = recruteurToken("rec.ia@test.local");
        mockMvc.perform(post("/api/offres/ia/description").header("Authorization", bearer(recruteur))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titrePoste\":\"Développeur Java\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").exists());
    }

    // ------------------------------------------------------------------ helpers

    private String recruteurToken(String email) throws Exception {
        createCandidateAndGetToken(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        mockMvc.perform(put("/api/admin/users/" + user.getId() + "/role")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RECRUITER\"}"))
                .andExpect(status().isOk());
        return loginAndGetToken(email, DEFAULT_PASSWORD);
    }

    private long creerOffre(String token, String titre, boolean publier) throws Exception {
        String response = mockMvc.perform(post("/api/offres").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(offre(titre, publier))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value(publier ? "PUBLIEE" : "BROUILLON"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asLong();
    }

    private Map<String, Object> offre(String titre, Boolean publier) {
        Map<String, Object> body = new HashMap<>();
        body.put("titrePoste", titre);
        body.put("description", "Développement d'API REST avec Spring Boot dans une équipe agile.");
        body.put("niveauRequis", "Junior");
        body.put("competencesRequises", "Java, Spring Boot, SQL");
        body.put("localisation", "Tunis");
        body.put("typeContrat", "CDI");
        body.put("publier", publier);
        return body;
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
