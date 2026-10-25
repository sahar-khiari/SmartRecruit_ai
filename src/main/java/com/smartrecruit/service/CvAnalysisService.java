package com.smartrecruit.service;

import com.smartrecruit.dto.request.CvAnalysisApplyRequest;
import com.smartrecruit.dto.response.CandidateProfileResponse;
import com.smartrecruit.dto.response.CvAnalysisResponse;

/**
 * Analyse IA du CV courant du candidat connecté (Module 1 - étape 6).
 */
public interface CvAnalysisService {

    /** Envoie le CV courant au service IA ; l'analyse est enregistrée même en cas d'échec (traçabilité). */
    CvAnalysisResponse analyzeMyCv();

    CvAnalysisResponse getMyLatestAnalysis();

    /** Copie dans le profil les valeurs validées par le candidat. */
    CandidateProfileResponse applyMyAnalysis(CvAnalysisApplyRequest request);
}
