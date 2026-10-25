package com.smartrecruit;

import com.smartrecruit.dto.request.UpdateUserRoleRequest;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.repository.CandidateCvRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CandidateCvIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private CandidateCvRepository cvRepository;

    @Value("${app.cv.storage-dir}")
    private String storageDir;

    @Test
    @DisplayName("Un candidat dépose un PDF puis consulte son CV courant")
    void upload_validPdf_thenGetMyCv() throws Exception {
        String token = createCandidateAndGetToken("cv@test.local");

        upload(token, pdf("Mon CV.pdf", PDF))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("Mon CV.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length));

        mockMvc.perform(get("/api/candidates/me/cv").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("Mon CV.pdf"));
    }

    @Test
    @DisplayName("Le dernier CV déposé devient le CV courant")
    void upload_twice_lastOneIsCurrent() throws Exception {
        String token = createCandidateAndGetToken("deux.cv@test.local");

        upload(token, pdf("ancien.pdf", PDF)).andExpect(status().isCreated());
        upload(token, pdf("nouveau.pdf", PDF)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/candidates/me/cv").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("nouveau.pdf"));
    }

    @Test
    @DisplayName("GET /api/candidates/me/cv renvoie 404 sans CV")
    void getMyCv_withoutCv_returns404() throws Exception {
        String token = createCandidateAndGetToken("sans.cv@test.local");

        mockMvc.perform(get("/api/candidates/me/cv").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un fichier texte renommé en .pdf est refusé (400)")
    void upload_fakePdf_returns400() throws Exception {
        String token = createCandidateAndGetToken("faux.pdf@test.local");

        upload(token, pdf("cv.pdf", "Ceci n'est pas un PDF".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le fichier n'est pas un PDF valide"));
    }

    @Test
    @DisplayName("Une autre extension que .pdf est refusée (400)")
    void upload_wrongExtension_returns400() throws Exception {
        String token = createCandidateAndGetToken("docx@test.local");

        upload(token, new MockMultipartFile("file", "cv.docx", "application/octet-stream", PDF))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un fichier vide est refusé (400)")
    void upload_empty_returns400() throws Exception {
        String token = createCandidateAndGetToken("vide.cv@test.local");

        upload(token, pdf("cv.pdf", new byte[0]))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un PDF de plus de 5 Mo est refusé (413)")
    void upload_tooLarge_returns413() throws Exception {
        String token = createCandidateAndGetToken("gros.cv@test.local");
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);

        upload(token, pdf("gros.pdf", big))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("Un recruteur ne peut pas déposer de CV (403)")
    void recruiter_cannotUpload() throws Exception {
        String token = createRecruiterAndGetToken("recruteur.cv@test.local");

        upload(token, pdf("cv.pdf", PDF)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Le recruteur et le candidat lui-même téléchargent le PDF ; un autre candidat est refusé")
    void download_accessRules() throws Exception {
        String ownerToken = createCandidateAndGetToken("proprio@test.local");
        upload(ownerToken, pdf("CV Ahmed.pdf", PDF)).andExpect(status().isCreated());
        Long ownerId = userRepository.findByEmail("proprio@test.local").orElseThrow().getId();

        mockMvc.perform(get("/api/candidates/{id}/cv/file", ownerId)
                        .header("Authorization", "Bearer " + createRecruiterAndGetToken("rh@test.local")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(content().bytes(PDF));

        mockMvc.perform(get("/api/candidates/{id}/cv/file", ownerId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/candidates/{id}/cv/file", ownerId)
                        .header("Authorization", "Bearer " + createCandidateAndGetToken("voisin@test.local")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Télécharger le CV d'un candidat sans CV renvoie 404")
    void download_withoutCv_returns404() throws Exception {
        createCandidateAndGetToken("pas.de.cv@test.local");
        Long id = userRepository.findByEmail("pas.de.cv@test.local").orElseThrow().getId();

        mockMvc.perform(get("/api/candidates/{id}/cv/file", id).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("La suppression d'un candidat par l'admin supprime aussi ses fichiers PDF")
    void deleteCandidate_removesCvFiles() throws Exception {
        String token = createCandidateAndGetToken("supprime.cv@test.local");
        upload(token, pdf("cv.pdf", PDF)).andExpect(status().isCreated());
        Long id = userRepository.findByEmail("supprime.cv@test.local").orElseThrow().getId();
        List<String> keys = cvRepository.findStorageKeysByUserId(id);
        assertThat(keys).hasSize(1);
        assertThat(Path.of(storageDir, keys.get(0))).exists();

        mockMvc.perform(delete("/api/admin/candidates/{id}", id).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        assertThat(Path.of(storageDir, keys.get(0))).doesNotExist();
    }

    private MockMultipartFile pdf(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, MediaType.APPLICATION_PDF_VALUE, content);
    }

    private ResultActions upload(String token, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/candidates/me/cv").file(file)
                .header("Authorization", "Bearer " + token));
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
