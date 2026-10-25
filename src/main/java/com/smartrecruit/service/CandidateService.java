package com.smartrecruit.service;

import com.smartrecruit.dto.request.AdminCandidateCreateRequest;
import com.smartrecruit.dto.request.CandidateProfileUpdateRequest;
import com.smartrecruit.dto.request.CandidateSkillsUpdateRequest;
import com.smartrecruit.dto.request.CvAnalysisApplyRequest;
import com.smartrecruit.dto.response.CandidateProfileResponse;
import com.smartrecruit.dto.response.CandidateSummaryResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.ExperienceLevel;
import org.springframework.data.domain.Pageable;

import java.util.Set;

/**
 * Gestion des candidats (Module 1). L'identifiant d'un candidat est l'id de son compte utilisateur.
 */
public interface CandidateService {

    CandidateProfileResponse getMyProfile();

    CandidateProfileResponse updateMyProfile(CandidateProfileUpdateRequest request);

    CandidateProfileResponse updateMySkills(CandidateSkillsUpdateRequest request);

    PageResponse<CandidateSummaryResponse> searchCandidates(String search, ExperienceLevel level, Pageable pageable);

    CandidateProfileResponse getCandidate(Long id);

    CandidateProfileResponse createCandidate(AdminCandidateCreateRequest request);

    CandidateProfileResponse updateCandidate(Long id, CandidateProfileUpdateRequest request);

    CandidateProfileResponse updateCandidateSkills(Long id, CandidateSkillsUpdateRequest request);

    void deleteCandidate(Long id);

    /**
     * Applique au profil du candidat connecté les valeurs qu'il a validées à partir d'une analyse IA.
     * {@code aiSkillNames} : noms (en minuscules) proposés par l'IA, pour marquer l'origine des compétences.
     */
    CandidateProfileResponse applyCvAnalysis(CvAnalysisApplyRequest request, Set<String> aiSkillNames);
}
