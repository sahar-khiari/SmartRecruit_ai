package com.smartrecruit.matching.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.matching.dto.CandidatProfileResponse;
import com.smartrecruit.matching.dto.SearchRequest;
import com.smartrecruit.matching.engine.SkillDictionary;
import com.smartrecruit.matching.entity.CandidatProfile;
import com.smartrecruit.matching.repository.CandidatProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Recherche de profils candidats par mots-clés et compétences (Layer 0).
 *
 * Algorithme :
 *   1. Parse les compétences de la requête via SkillDictionary.
 *   2. Filtre les profils dont skillsJson contient au moins une compétence demandée.
 *   3. Filtre optionnel sur le niveau.
 *   4. Tri par taux de recouvrement décroissant.
 *   5. Pagination manuelle (évite de charger tout en base dans un vrai contexte).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final CandidatProfileRepository repo;
    private final SkillDictionary           skillDict;
    private final ObjectMapper              objectMapper;
    private final CandidatProfileService    profileService;

    @Transactional(readOnly = true)
    public List<CandidatProfileResponse> search(SearchRequest req) {
        // Extraction des compétences de la requête texte
        Set<String> querySkills = new LinkedHashSet<>();
        if (req.query() != null && !req.query().isBlank()) {
            querySkills.addAll(skillDict.extractSkills(req.query()));
        }
        if (req.skills() != null) {
            req.skills().stream()
               .map(skillDict::canonical)
               .filter(Objects::nonNull)
               .forEach(querySkills::add);
        }

        List<CandidatProfile> all = repo.findAll();

        List<CandidatProfile> filtered = all.stream()
                .filter(p -> matchesNiveau(p, req.niveau()))
                .filter(p -> matchesSkills(p, querySkills))
                .sorted((a, b) -> Double.compare(
                        coverageScore(b, querySkills),
                        coverageScore(a, querySkills)))
                .collect(Collectors.toList());

        // Pagination
        int page = Math.max(0, req.page());
        int size = Math.max(1, Math.min(50, req.size() > 0 ? req.size() : 10));
        int from = page * size;
        int to   = Math.min(from + size, filtered.size());

        if (from >= filtered.size()) return List.of();

        return filtered.subList(from, to).stream()
                .map(p -> profileService.getProfileByUserId(p.getUserId()))
                .collect(Collectors.toList());
    }

    private boolean matchesNiveau(CandidatProfile p, String niveau) {
        if (niveau == null || niveau.isBlank()) return true;
        return niveau.equalsIgnoreCase(p.getNiveau());
    }

    private boolean matchesSkills(CandidatProfile p, Set<String> querySkills) {
        if (querySkills.isEmpty()) return true;
        Set<String> profileSkills = parseSkills(p);
        return querySkills.stream().anyMatch(profileSkills::contains);
    }

    private double coverageScore(CandidatProfile p, Set<String> querySkills) {
        if (querySkills.isEmpty()) return 0.0;
        Set<String> profileSkills = parseSkills(p);
        long matches = querySkills.stream().filter(profileSkills::contains).count();
        return (double) matches / querySkills.size();
    }

    private Set<String> parseSkills(CandidatProfile p) {
        if (p.getSkillsJson() == null) return Set.of();
        try {
            List<String> list = objectMapper.readValue(p.getSkillsJson(),
                    new TypeReference<List<String>>() {});
            return new HashSet<>(list);
        } catch (Exception e) {
            return Set.of();
        }
    }
}
