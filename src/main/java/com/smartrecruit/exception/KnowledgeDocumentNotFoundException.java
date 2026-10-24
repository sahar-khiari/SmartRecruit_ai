package com.smartrecruit.exception;

/**
 * Document de la base de connaissances RH introuvable. HTTP 404.
 */
public class KnowledgeDocumentNotFoundException extends ResourceNotFoundException {

    public KnowledgeDocumentNotFoundException() {
        super("Document de la base de connaissances introuvable");
    }

    public KnowledgeDocumentNotFoundException(Long id) {
        super("Document de la base de connaissances introuvable pour id = " + id);
    }
}
