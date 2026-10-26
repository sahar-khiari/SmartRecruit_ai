package com.smartrecruit.integration.ai;

import com.smartrecruit.dto.ai.AiCvAnalysisResponse;
import com.smartrecruit.dto.ai.AiCvCoachResponse;
import com.smartrecruit.exception.CvAnalysisException;

/**
 * Client du service IA pour l'analyse de CV (Module 1).
 * Séparé du client IA commun, qui n'est pas modifié.
 */
public interface CvAnalysisClient {

    /**
     * Envoie le PDF au service IA et renvoie l'analyse.
     *
     * @throws CvAnalysisException si le service refuse le fichier, échoue ou est injoignable
     */
    AiCvAnalysisResponse analyze(byte[] pdf);

    /**
     * Coach CV : score par règles et conseils d'amélioration.
     *
     * @throws CvAnalysisException si le service refuse le fichier, échoue ou est injoignable
     */
    AiCvCoachResponse coach(byte[] pdf);
}
