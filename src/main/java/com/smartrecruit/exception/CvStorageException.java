package com.smartrecruit.exception;

/**
 * Erreur d'écriture ou de lecture d'un CV sur le disque. HTTP 500, sans détail technique pour le client.
 */
public class CvStorageException extends RuntimeException {

    public CvStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
