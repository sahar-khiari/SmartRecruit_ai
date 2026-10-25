package com.smartrecruit.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * CV refusé : vide, trop volumineux (413) ou qui n'est pas un vrai PDF (400).
 */
@Getter
public class InvalidCvFileException extends RuntimeException {

    private final HttpStatus status;

    public InvalidCvFileException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
