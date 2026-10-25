package com.smartrecruit.exception;

import com.smartrecruit.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Erreurs propres au dépôt de CV (Module 1). Séparé du GlobalExceptionHandler commun, qui n'est pas modifié.
 * Priorité maximale : sinon le gestionnaire générique (Exception -> 500) du handler commun répondrait à sa place.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CandidateCvExceptionHandler {

    @ExceptionHandler(InvalidCvFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCv(InvalidCvFileException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Le CV ne doit pas dépasser 5 Mo", request);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> handleMissingFile(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Envoyez le CV dans le champ multipart « file »", request);
    }

    @ExceptionHandler(CvAnalysisException.class)
    public ResponseEntity<ErrorResponse> handleAnalysis(CvAnalysisException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(CvStorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(CvStorageException ex, HttpServletRequest request) {
        log.error("Stockage des CV en échec sur {} : {}", request.getRequestURI(), ex.getMessage(), ex.getCause());
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Le CV n'a pas pu être traité. Veuillez réessayer plus tard", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(
                status.value(), status.getReasonPhrase(), message, request.getRequestURI(), null));
    }
}
