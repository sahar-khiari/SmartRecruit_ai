package com.smartrecruit.exception;

import org.springframework.http.HttpStatus;

/**
 * Erreur métier contrôlée de l'Assistant RH. Le message est sûr à exposer à
 * l'utilisateur : aucune URL interne, clé, prompt ou stack trace.
 */
public class HrAssistantException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public HrAssistantException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
