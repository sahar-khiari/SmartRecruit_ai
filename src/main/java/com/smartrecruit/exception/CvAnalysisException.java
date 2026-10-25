package com.smartrecruit.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Échec de l'analyse IA d'un CV. {@code code} reprend le code stable du service IA
 * (NOT_PDF, NO_TEXT, LLM_UNAVAILABLE...) ou un code Spring (AI_SERVICE_UNAVAILABLE, ANALYSIS_IN_PROGRESS...).
 */
@Getter
public class CvAnalysisException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CvAnalysisException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
