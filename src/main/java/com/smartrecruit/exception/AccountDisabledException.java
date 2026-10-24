package com.smartrecruit.exception;

/**
 * Le compte existe mais a été désactivé par un administrateur. HTTP 403.
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("Votre compte est désactivé. Veuillez contacter un administrateur");
    }
}
