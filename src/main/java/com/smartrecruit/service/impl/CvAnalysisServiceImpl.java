package com.smartrecruit.service.impl;

import com.smartrecruit.dto.ai.AiCvAnalysisResponse;
import com.smartrecruit.dto.request.CvAnalysisApplyRequest;
import com.smartrecruit.dto.response.CandidateProfileResponse;
import com.smartrecruit.dto.response.CvAnalysisResponse;
import com.smartrecruit.entity.CandidateCv;
import com.smartrecruit.entity.CvAnalysis;
import com.smartrecruit.entity.CvAnalysisStatus;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.CvAnalysisException;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.integration.ai.CvAnalysisClient;
import com.smartrecruit.mapper.CvAnalysisMapper;
import com.smartrecruit.repository.CandidateCvRepository;
import com.smartrecruit.repository.CvAnalysisRepository;
import com.smartrecruit.service.CandidateCvService;
import com.smartrecruit.service.CandidateService;
import com.smartrecruit.service.CurrentUserService;
import com.smartrecruit.service.CvAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CvAnalysisServiceImpl implements CvAnalysisService {

    /** Au-delà, une analyse PENDING est considérée comme abandonnée (crash pendant l'appel). */
    private static final int PENDING_TIMEOUT_MINUTES = 3;

    private final CvAnalysisRepository analysisRepository;
    private final CandidateCvRepository cvRepository;
    private final CandidateCvService cvService;
    private final CandidateService candidateService;
    private final CurrentUserService currentUserService;
    private final CvAnalysisClient client;
    private final CvAnalysisMapper mapper;

    /**
     * Pas de @Transactional : l'appel au service IA (~20 s) ne doit pas garder une transaction ouverte.
     * Chaque save() est sa propre transaction : PENDING est visible pendant l'appel.
     */
    @Override
    public CvAnalysisResponse analyzeMyCv() {
        User user = currentUserService.getCurrentUser();
        CandidateCv cv = cvRepository.findFirstByProfile_User_IdOrderByUploadedAtDescIdDesc(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Déposez d'abord votre CV avant de lancer l'analyse"));

        if (analysisRepository.existsByCv_Profile_User_IdAndStatusAndCreatedAtAfter(
                user.getId(), CvAnalysisStatus.PENDING, LocalDateTime.now().minusMinutes(PENDING_TIMEOUT_MINUTES))) {
            throw new CvAnalysisException(HttpStatus.CONFLICT, "ANALYSIS_IN_PROGRESS",
                    "Une analyse de votre CV est déjà en cours");
        }

        byte[] pdf = cvService.readContent(cv);
        CvAnalysis analysis = analysisRepository.save(
                CvAnalysis.builder().cv(cv).status(CvAnalysisStatus.PENDING).build());
        long started = System.nanoTime();

        try {
            AiCvAnalysisResponse result = client.analyze(pdf);
            complete(analysis, result, elapsedMs(started));
            log.info("Analyse de CV terminée | analysisId={} model={} secours={} durationMs={}",
                    analysis.getId(), analysis.getModel(), analysis.getFallbackUsed(), analysis.getDurationMs());
            return mapper.toResponse(analysisRepository.save(analysis));
        } catch (CvAnalysisException ex) {
            fail(analysis, ex.getCode(), ex.getMessage(), elapsedMs(started));
            throw ex;
        } catch (RuntimeException ex) {
            fail(analysis, "INTERNAL_ERROR", "Erreur interne pendant l'analyse", elapsedMs(started));
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CvAnalysisResponse getMyLatestAnalysis() {
        Long userId = currentUserService.getCurrentUser().getId();
        return analysisRepository.findFirstByCv_Profile_User_IdOrderByCreatedAtDescIdDesc(userId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune analyse de CV pour le moment"));
    }

    @Override
    @Transactional
    public CandidateProfileResponse applyMyAnalysis(CvAnalysisApplyRequest request) {
        Long userId = currentUserService.getCurrentUser().getId();
        // Même 404 si l'analyse n'existe pas ou appartient à un autre candidat.
        CvAnalysis analysis = analysisRepository.findByIdAndCv_Profile_User_Id(request.analysisId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Analyse", "id", request.analysisId()));
        if (analysis.getStatus() != CvAnalysisStatus.COMPLETED) {
            throw new CvAnalysisException(HttpStatus.CONFLICT, "ANALYSIS_NOT_COMPLETED",
                    "Seule une analyse terminée peut être appliquée au profil");
        }

        Set<String> aiSkillNames = mapper.readResult(analysis)
                .map(AiCvAnalysisResponse::profile)
                .map(AiCvAnalysisResponse.Profile::skills)
                .orElse(List.of())
                .stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        CandidateProfileResponse profile = candidateService.applyCvAnalysis(request, aiSkillNames);
        analysis.setAppliedAt(LocalDateTime.now());
        return profile;
    }

    private void complete(CvAnalysis analysis, AiCvAnalysisResponse result, long durationMs) {
        analysis.setStatus(CvAnalysisStatus.COMPLETED);
        analysis.setResultJson(mapper.toJson(result));
        analysis.setPipelineVersion(result.pipelineVersion());
        if (result.llm() != null) {
            analysis.setModel(result.llm().model());
            analysis.setFallbackUsed(result.llm().fallbackUsed());
            analysis.setPromptVersion(result.llm().promptVersion());
            analysis.setInputTokens(result.llm().inputTokens());
            analysis.setOutputTokens(result.llm().outputTokens());
        }
        analysis.setDurationMs(durationMs);
        analysis.setCompletedAt(LocalDateTime.now());
    }

    private void fail(CvAnalysis analysis, String code, String message, long durationMs) {
        analysis.setStatus(CvAnalysisStatus.FAILED);
        analysis.setErrorCode(code);
        analysis.setErrorMessage(message != null && message.length() > 255 ? message.substring(0, 255) : message);
        analysis.setDurationMs(durationMs);
        analysis.setCompletedAt(LocalDateTime.now());
        analysisRepository.save(analysis);
        log.warn("Analyse de CV en échec | analysisId={} code={}", analysis.getId(), code);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
