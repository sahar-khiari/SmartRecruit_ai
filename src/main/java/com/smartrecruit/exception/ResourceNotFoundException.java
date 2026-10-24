package com.smartrecruit.exception;

/**
 * Ressource (utilisateur, rôle...) introuvable. HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s introuvable pour %s = %s", resource, field, value));
    }
}
