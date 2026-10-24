package com.smartrecruit.exception;

/**
 * Un compte utilise déjà l'adresse email fournie. HTTP 409.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Un utilisateur avec l'adresse email '" + email + "' existe déjà");
    }
}
