package com.smartrecruit.matching.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.matching.dto.CandidatProfileRequest;
import com.smartrecruit.matching.dto.CandidatProfileResponse;
import com.smartrecruit.matching.engine.JavaMatchingEngine;
import com.smartrecruit.matching.engine.SkillDictionary;
import com.smartrecruit.matching.entity.CandidatProfile;
import com.smartrecruit.matching.repository.CandidatProfileRepository;
import com.smartrecruit.repository.UserRepository;
import com.smartrecruit.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service de gestion du profil candidat pour le module matching.
 *
 * Fonctionnalités :
 * - Créer / mettre à jour un profil (texte collé ou upload PDF)
 * - Extraction automatique des compétences via SkillDictionary (Layer 0)
 * - Accès en lecture pour les recruteurs et admins
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CandidatProfileService {

    private final CandidatProfileRepository repo;
    private final CurrentUserService        currentUserService;
    private final UserRepository            userRepo;
    private final SkillDictionary           skillDict;
    private final JavaMatchingEngine        javaEngine;
    private final ObjectMapper              objectMapper;

    /** Retourne le profil du candidat connecté. */
    @Transactional(readOnly = true)
    public CandidatProfileResponse getMonProfil() {
        User me = currentUserService.getCurrentUser();
        CandidatProfile profile = repo.findByUserId(me.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun profil matching trouvé. Créez votre profil CV d'abord."));
        return toResponse(profile);
    }

    /** Crée ou met à jour le profil du candidat connecté. */
    @Transactional
    public CandidatProfileResponse saveMonProfil(CandidatProfileRequest req) {
        User me = currentUserService.getCurrentUser();
        CandidatProfile profile = repo.findByUserId(me.getId())
                .orElse(CandidatProfile.builder().userId(me.getId()).build());

        updateFields(profile, req);
        return toResponse(repo.save(profile));
    }

    /**
     * Traite un PDF uploadé : extrait le texte brut et met à jour le profil.
     * L'extraction est basique (lecture bytes UTF-8) ; pour un vrai PDF
     * il faudrait Apache PDFBox (non ajouté pour rester additive).
     * CHOIX DOCUMENTÉ : l'extraction PDF avancée est notée comme limitation.
     */
    @Transactional
    public CandidatProfileResponse uploadPdf(MultipartFile file, String niveau) throws IOException {
        User me = currentUserService.getCurrentUser();

        String cvText;
        String contentType = file.getContentType();
        if (contentType != null && contentType.contains("pdf")) {
            // Extraction simplifiée : lecture du texte lisible dans le PDF
            // (fonctionne pour les PDF texte, pas les PDF scan/image)
            cvText = extractTextFromPdf(file.getBytes());
        } else {
            // Fichier texte brut
            cvText = new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        CandidatProfileRequest req = new CandidatProfileRequest(cvText, niveau, null);
        CandidatProfile profile = repo.findByUserId(me.getId())
                .orElse(CandidatProfile.builder().userId(me.getId()).build());

        updateFields(profile, req);
        return toResponse(repo.save(profile));
    }

    /** Retourne le profil d'un candidat pour un recruteur/admin (lecture seule). */
    @Transactional(readOnly = true)
    public CandidatProfileResponse getProfileByUserId(Long userId) {
        CandidatProfile profile = repo.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil candidat introuvable pour l'utilisateur : " + userId));
        return toResponse(profile);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void updateFields(CandidatProfile profile, CandidatProfileRequest req) {
        if (req.cvText() != null) {
            profile.setCvText(req.cvText());
            // Extraction automatique des compétences (layer 0)
            Set<String> skills = skillDict.extractSkills(req.cvText());
            try {
                profile.setSkillsJson(objectMapper.writeValueAsString(new ArrayList<>(skills)));
            } catch (Exception e) {
                log.warn("[Matching] Erreur sérialisation compétences : {}", e.getMessage());
                profile.setSkillsJson("[]");
            }
        }
        if (req.niveau() != null)               profile.setNiveau(req.niveau());
        if (req.resumeProfessionnel() != null)  profile.setResumeProfessionnel(req.resumeProfessionnel());
    }

    private CandidatProfileResponse toResponse(CandidatProfile p) {
        List<String> skills = List.of();
        try {
            if (p.getSkillsJson() != null) {
                skills = objectMapper.readValue(p.getSkillsJson(),
                        new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {}

        // Preview : 500 premiers caractères du CV, jamais le texte entier dans la liste
        String preview = p.getCvText() != null && p.getCvText().length() > 500
                ? p.getCvText().substring(0, 500) + "…"
                : p.getCvText();

        return new CandidatProfileResponse(
                p.getId(), p.getUserId(), preview,
                skills, p.getNiveau(), p.getResumeProfessionnel(), p.getUpdatedAt());
    }

    /**
     * Extraction de texte lisible depuis un PDF (heuristique simple).
     * Pour les PDF non textuels (scans), retourne un message d'information.
     */
    private String extractTextFromPdf(byte[] bytes) {
        // Heuristique : extraire les séquences de texte lisible entre BT et ET
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        StringBuilder sb = new StringBuilder();
        // Recherche de texte entre parenthèses (opérateurs Tj, TJ dans un PDF)
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\(([^\\\\)]{2,200})\\)\\s*Tj")
                .matcher(raw);
        int count = 0;
        while (m.find() && count < 5000) {
            String token = m.group(1).trim();
            if (!token.isBlank()) {
                sb.append(token).append(" ");
                count++;
            }
        }
        if (sb.length() < 100) {
            return "[PDF non texte ou extraction partielle. Collez le texte directement.]\n"
                    + "(Texte brut partiel) : " + raw.substring(0, Math.min(1000, raw.length()));
        }
        return sb.toString().trim();
    }
}
