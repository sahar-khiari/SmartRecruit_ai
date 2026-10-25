package com.smartrecruit.service.impl;

import com.smartrecruit.dto.request.AdminCandidateCreateRequest;
import com.smartrecruit.dto.request.CandidateProfileUpdateRequest;
import com.smartrecruit.dto.request.CandidateSkillsUpdateRequest;
import com.smartrecruit.dto.request.CvAnalysisApplyRequest;
import com.smartrecruit.dto.response.CandidateProfileResponse;
import com.smartrecruit.dto.response.CandidateSummaryResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.CandidateProfile;
import com.smartrecruit.entity.CandidateSkill;
import com.smartrecruit.entity.ExperienceLevel;
import com.smartrecruit.entity.Role;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.SkillSource;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.CandidateNotFoundException;
import com.smartrecruit.exception.EmailAlreadyExistsException;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.mapper.CandidateProfileMapper;
import com.smartrecruit.repository.CandidateProfileRepository;
import com.smartrecruit.repository.RoleRepository;
import com.smartrecruit.repository.UserRepository;
import com.smartrecruit.service.CandidateCvService;
import com.smartrecruit.service.CandidateService;
import com.smartrecruit.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateServiceImpl implements CandidateService {

    private final CandidateProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final CandidateCvService cvService;
    private final CandidateProfileMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public CandidateProfileResponse getMyProfile() {
        User user = currentUserService.getCurrentUser();
        return mapper.toResponse(user, profileRepository.findByUser_Id(user.getId()).orElse(null));
    }

    @Override
    @Transactional
    public CandidateProfileResponse updateMyProfile(CandidateProfileUpdateRequest request) {
        return saveProfile(currentUserService.getCurrentUser(), request);
    }

    @Override
    @Transactional
    public CandidateProfileResponse updateMySkills(CandidateSkillsUpdateRequest request) {
        return saveSkills(currentUserService.getCurrentUser(), request);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CandidateSummaryResponse> searchCandidates(String search, ExperienceLevel level,
                                                                   Pageable pageable) {
        String pattern = StringUtils.hasText(search)
                ? "%" + search.trim().toLowerCase(Locale.ROOT) + "%"
                : null;
        Page<User> users = profileRepository.searchCandidates(pattern, level, pageable);

        Map<Long, CandidateProfile> profilesByUserId = profileRepository
                .findAllByUser_IdIn(users.getContent().stream().map(User::getId).toList())
                .stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity()));

        return PageResponse.from(users, user -> mapper.toSummary(user, profilesByUserId.get(user.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateProfileResponse getCandidate(Long id) {
        User user = findCandidateUser(id);
        return mapper.toResponse(user, profileRepository.findByUser_Id(id).orElse(null));
    }

    @Override
    @Transactional
    public CandidateProfileResponse createCandidate(AdminCandidateCreateRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        Role candidateRole = roleRepository.findByName(RoleName.CANDIDATE)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle", "name", RoleName.CANDIDATE));

        User user = userRepository.save(User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .enabled(true)
                .role(candidateRole)
                .build());

        if (request.profile() == null) {
            return mapper.toResponse(user, null);
        }
        return saveProfile(user, request.profile());
    }

    @Override
    @Transactional
    public CandidateProfileResponse updateCandidate(Long id, CandidateProfileUpdateRequest request) {
        return saveProfile(findCandidateUser(id), request);
    }

    @Override
    @Transactional
    public CandidateProfileResponse updateCandidateSkills(Long id, CandidateSkillsUpdateRequest request) {
        return saveSkills(findCandidateUser(id), request);
    }

    @Override
    @Transactional
    public void deleteCandidate(Long id) {
        // Profil, compétences et métadonnées des CV : supprimés par la base (FK ON DELETE CASCADE).
        // flush immédiat : une candidature liée donne un 409 ici, avant de toucher aux fichiers.
        User user = findCandidateUser(id);
        List<String> cvFiles = cvService.findStorageKeys(id);
        userRepository.delete(user);
        userRepository.flush();
        cvService.deleteFiles(cvFiles);
    }

    private CandidateProfileResponse saveProfile(User user, CandidateProfileUpdateRequest request) {
        CandidateProfile profile = getOrCreateProfile(user);

        profile.setHeadline(clean(request.headline()));
        profile.setPhone(clean(request.phone()));
        profile.setCity(clean(request.city()));
        profile.setGithubUrl(clean(request.githubUrl()));
        profile.setLinkedinUrl(clean(request.linkedinUrl()));
        profile.setSummary(clean(request.summary()));
        profile.setYearsOfExperience(request.yearsOfExperience());
        profile.setExperienceLevel(resolveLevel(request.experienceLevel(), request.yearsOfExperience()));

        return mapper.toResponse(user, profileRepository.save(profile));
    }

    private CandidateProfileResponse saveSkills(User user, CandidateSkillsUpdateRequest request) {
        CandidateProfile profile = getOrCreateProfile(user);
        replaceSkills(profile, request.skills(), Set.of());
        CandidateProfile saved = profileRepository.saveAndFlush(profile);
        saved.getSkills().sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return mapper.toResponse(user, saved);
    }

    @Override
    @Transactional
    public CandidateProfileResponse applyCvAnalysis(CvAnalysisApplyRequest request, Set<String> aiSkillNames) {
        User user = currentUserService.getCurrentUser();
        CandidateProfile profile = getOrCreateProfile(user);

        // Seuls les champs issus du CV sont remplacés : téléphone, ville et liens restent inchangés.
        profile.setHeadline(clean(request.headline()));
        profile.setSummary(clean(request.summary()));
        profile.setYearsOfExperience(request.yearsOfExperience());
        profile.setExperienceLevel(resolveLevel(null, request.yearsOfExperience()));
        replaceSkills(profile, request.skills(), aiSkillNames);

        CandidateProfile saved = profileRepository.saveAndFlush(profile);
        saved.getSkills().sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return mapper.toResponse(user, saved);
    }

    /**
     * Remplace la liste des compétences. Les compétences déjà présentes (même nom, casse ignorée)
     * sont conservées avec leur origine, pour ne pas supprimer puis recréer la même ligne.
     * Une nouvelle compétence est AI si elle figure dans {@code aiSkillNames} (en minuscules), sinon MANUAL.
     */
    private void replaceSkills(CandidateProfile profile, List<String> names, Set<String> aiSkillNames) {
        Map<String, String> requested = new LinkedHashMap<>();
        for (String skill : names) {
            String name = skill.trim().replaceAll("\\s+", " ");
            requested.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }

        List<CandidateSkill> skills = profile.getSkills();
        skills.removeIf(skill -> !requested.containsKey(skill.getName().toLowerCase(Locale.ROOT)));

        for (CandidateSkill skill : skills) {
            skill.setName(requested.remove(skill.getName().toLowerCase(Locale.ROOT)));
        }
        requested.forEach((key, name) -> skills.add(CandidateSkill.builder()
                .profile(profile)
                .name(name)
                .source(aiSkillNames.contains(key) ? SkillSource.AI : SkillSource.MANUAL)
                .build()));
    }

    private CandidateProfile getOrCreateProfile(User user) {
        return profileRepository.findByUser_Id(user.getId())
                .orElseGet(() -> CandidateProfile.builder().user(user).build());
    }

    private User findCandidateUser(Long id) {
        return userRepository.findById(id)
                .filter(user -> user.getRole() != null && user.getRole().getName() == RoleName.CANDIDATE)
                .orElseThrow(() -> new CandidateNotFoundException(id));
    }

    private ExperienceLevel resolveLevel(ExperienceLevel level, Integer years) {
        if (level != null) {
            return level;
        }
        return years != null ? ExperienceLevel.fromYears(years) : null;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
