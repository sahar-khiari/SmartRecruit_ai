package com.smartrecruit.mapper;

import com.smartrecruit.dto.response.CandidateProfileResponse;
import com.smartrecruit.dto.response.CandidateSkillResponse;
import com.smartrecruit.dto.response.CandidateSummaryResponse;
import com.smartrecruit.entity.CandidateProfile;
import com.smartrecruit.entity.CandidateSkill;
import com.smartrecruit.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convertit un compte candidat et son profil (éventuellement absent) en DTO.
 * Un candidat qui n'a pas encore enregistré son profil est renvoyé avec des champs métier vides.
 */
@Component
public class CandidateProfileMapper {

    public CandidateProfileResponse toResponse(User user, CandidateProfile profile) {
        boolean hasProfile = profile != null;
        return new CandidateProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.isEnabled(),
                hasProfile,
                hasProfile ? profile.getHeadline() : null,
                hasProfile ? profile.getPhone() : null,
                hasProfile ? profile.getCity() : null,
                hasProfile ? profile.getGithubUrl() : null,
                hasProfile ? profile.getLinkedinUrl() : null,
                hasProfile ? profile.getSummary() : null,
                hasProfile ? profile.getExperienceLevel() : null,
                hasProfile ? profile.getYearsOfExperience() : null,
                hasProfile ? profile.getSkills().stream().map(this::toSkillResponse).toList() : List.of(),
                hasProfile ? profile.getCreatedAt() : null,
                hasProfile ? profile.getUpdatedAt() : null
        );
    }

    public CandidateSummaryResponse toSummary(User user, CandidateProfile profile) {
        boolean hasProfile = profile != null;
        return new CandidateSummaryResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.isEnabled(),
                hasProfile ? profile.getHeadline() : null,
                hasProfile ? profile.getCity() : null,
                hasProfile ? profile.getExperienceLevel() : null,
                hasProfile ? profile.getYearsOfExperience() : null,
                hasProfile ? profile.getSkills().stream().map(CandidateSkill::getName).sorted().toList() : List.of()
        );
    }

    private CandidateSkillResponse toSkillResponse(CandidateSkill skill) {
        return new CandidateSkillResponse(skill.getId(), skill.getName(), skill.getSource());
    }
}
