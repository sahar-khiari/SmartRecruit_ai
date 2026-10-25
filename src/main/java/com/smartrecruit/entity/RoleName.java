package com.smartrecruit.entity;

/**
 * Rôles disponibles dans la plateforme.
 * Un utilisateur possède un seul rôle (relation Role 1 ---- N User).
 */
public enum RoleName {
    ADMIN,
    RECRUITER,
    CANDIDATE
}
