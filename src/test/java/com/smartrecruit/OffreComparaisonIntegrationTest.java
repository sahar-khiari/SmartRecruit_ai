package com.smartrecruit;

import com.smartrecruit.dto.response.OffreComparaisonResponse;
import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.entity.StatutOffre;
import com.smartrecruit.integration.ai.AiServiceException;
import com.smartrecruit.integration.ai.OffreComparaisonClient;
import com.smartrecruit.repository.OffreEmploiRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module 2 : comparaison IA d'offres par un candidat. Le service IA (FastAPI + Cohere) est simulé.
 */
@Transactional
class OffreComparaisonIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private OffreComparaisonClient comparaisonClient;

    @Autowired
    private OffreEmploiRepository offreRepository;

    @Test
    @DisplayName("Un candidat compare 2 offres publiées : offres relues en base, profil transmis")
    @SuppressWarnings("unchecked")
    void candidate_comparesTwoOffers() throws Exception {
        Long java = offre("Développeur Java Spring Boot", "Junior", StatutOffre.PUBLIEE);
        Long fullStack = offre("Développeur Full-Stack Angular/Java", "Intermédiaire", StatutOffre.PUBLIEE);
        when(comparaisonClient.comparer(any())).thenReturn(new OffreComparaisonResponse(
                List.of(), new OffreComparaisonResponse.Recommandation(java, "L'offre A vous correspond."),
                true, "command-r-08-2024"));

        compare(createCandidateAndGetToken("alice.compare@test.local"),
                "{\"offreIds\": [" + java + ", " + fullStack + "], \"profil\": \"Junior, Java et Spring\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommandation.offreId").value(java))
                .andExpect(jsonPath("$.profilUtilise").value(true));

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(comparaisonClient).comparer(body.capture());
        List<Map<String, Object>> offres = (List<Map<String, Object>>) body.getValue().get("offres");
        assertThat(offres).extracting(o -> o.get("titrePoste"))
                .containsExactly("Développeur Java Spring Boot", "Développeur Full-Stack Angular/Java");
        assertThat(offres.get(0).get("niveauRequis")).isEqualTo("Junior");
        assertThat(body.getValue().get("profil")).isEqualTo("Junior, Java et Spring");
    }

    @Test
    @DisplayName("Une offre en brouillon ou clôturée ne peut pas être comparée (404), sans appel à l'IA")
    void closedOffer_isRejected() throws Exception {
        Long publiee = offre("Offre publiée", "Junior", StatutOffre.PUBLIEE);
        Long brouillon = offre("Offre brouillon", "Junior", StatutOffre.BROUILLON);

        compare(createCandidateAndGetToken("bob.compare@test.local"),
                "{\"offreIds\": [" + publiee + ", " + brouillon + "]}")
                .andExpect(status().isNotFound());
        verify(comparaisonClient, never()).comparer(any());
    }

    @Test
    @DisplayName("Il faut 2 ou 3 offres différentes (400)")
    void needsTwoOrThreeDistinctOffers() throws Exception {
        Long id = offre("Offre unique", "Junior", StatutOffre.PUBLIEE);
        String token = createCandidateAndGetToken("clara.compare@test.local");

        compare(token, "{\"offreIds\": [" + id + "]}").andExpect(status().isBadRequest());
        compare(token, "{\"offreIds\": [" + id + ", " + id + "]}").andExpect(status().isBadRequest());
        verify(comparaisonClient, never()).comparer(any());
    }

    @Test
    @DisplayName("IA indisponible : 503 avec un message clair")
    void aiDown_returns503() throws Exception {
        Long a = offre("Offre A", "Junior", StatutOffre.PUBLIEE);
        Long b = offre("Offre B", "Senior", StatutOffre.PUBLIEE);
        when(comparaisonClient.comparer(any())).thenThrow(
                new AiServiceException("Quota de l'IA atteint, réessayez dans quelques instants.", "AI_ERROR_429", null));

        compare(createCandidateAndGetToken("dan.compare@test.local"), "{\"offreIds\": [" + a + ", " + b + "]}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Quota de l'IA atteint, réessayez dans quelques instants."));
    }

    @Test
    @DisplayName("La comparaison est réservée aux candidats (403 pour l'admin)")
    void admin_isForbidden() throws Exception {
        Long a = offre("Offre A", "Junior", StatutOffre.PUBLIEE);
        Long b = offre("Offre B", "Senior", StatutOffre.PUBLIEE);

        compare(adminToken(), "{\"offreIds\": [" + a + ", " + b + "]}").andExpect(status().isForbidden());
    }

    private Long offre(String titre, String niveau, StatutOffre statut) {
        OffreEmploi offre = OffreEmploi.builder()
                .titrePoste(titre)
                .description("Description de l'offre " + titre)
                .niveauRequis(niveau)
                .competencesRequises("Java, Spring Boot")
                .localisation("Tunis")
                .typeContrat("CDI")
                .recruteur(userRepository.findByEmail(adminEmail).orElseThrow())
                .build();
        offre.changerStatut(statut);
        return offreRepository.save(offre).getId();
    }

    private ResultActions compare(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/offres/comparer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
