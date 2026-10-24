package com.smartrecruit.entity;

/**
 * Rôle d'un message dans une conversation avec l'Assistant RH.
 * USER : message saisi par l'utilisateur.
 * ASSISTANT : réponse produite ultérieurement par le service IA.
 * SYSTEM : instruction interne réservée au système.
 */
public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
