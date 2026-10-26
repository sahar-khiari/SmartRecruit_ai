package com.smartrecruit.service;

import com.smartrecruit.dto.request.CvAnalysisApplyRequest;
import com.smartrecruit.dto.response.CandidateProfileResponse;
import com.smartrecruit.dto.response.CvAnalysisResponse;
import com.smartrecruit.dto.response.CvCoachingResponse;

/**
 * Analyse IA du CV courant du candidat connecté (Module 1 - étape 6).
 */
public interface CvAnalysisService {

    /** Envoie le CV courant au service IA ; l'analyse est enregistrée même en cas d'échec (traçabilité). */
    CvAnalysisResponse analyzeMyCv();

    CvAnalysisResponse getMyLatestAnalysis();

    /** Copie dans le profil les valeurs validées par le candidat. */
    CandidateProfileResponse applyMyAnalysis(CvAnalysisApplyRequest request);

    /** Coach CV : score par règles et conseils d'amélioration du CV courant (enregistré pour la traçabilité). */
    CvCoachingResponse coachMyCv();

    CvCoachingResponse getMyLatestCoaching();
}
