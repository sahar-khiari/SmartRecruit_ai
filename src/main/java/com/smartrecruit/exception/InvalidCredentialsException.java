package com.smartrecruit.exception;

/**
 * Identifiants invalides. Le message reste volontairement générique. HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email ou mot de passe incorrect");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
