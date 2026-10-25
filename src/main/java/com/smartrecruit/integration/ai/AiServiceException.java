package com.smartrecruit.integration.ai;

/**
 * Erreur contrôlée du service interne. Le message est destiné à l'utilisateur
 * ou à l'administrateur et ne contient ni clé interne ni stack trace.
 */
public class AiServiceException extends RuntimeException {

    private final String errorCode;

    public AiServiceException(String message) {
        this(message, "AI_SERVICE_UNAVAILABLE", null);
    }

    public AiServiceException(String message, Throwable cause) {
        this(message, "AI_SERVICE_UNAVAILABLE", cause);
    }

    public AiServiceException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
