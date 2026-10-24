package com.smartrecruit.exception;

/**
 * Conversation inexistante ou non accessible par l'utilisateur connecté.
 * Le même 404 est utilisé dans les deux cas pour éviter toute fuite d'information.
 */
public class ConversationNotFoundException extends ResourceNotFoundException {

    public ConversationNotFoundException() {
        super("Conversation introuvable");
    }

    public ConversationNotFoundException(Long id) {
        super("Conversation introuvable pour id = " + id);
    }
}
