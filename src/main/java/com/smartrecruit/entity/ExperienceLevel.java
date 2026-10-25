package com.smartrecruit.entity;

/**
 * Niveau d'expérience d'un candidat (Module 1 - Candidats).
 * Règle métier : 0 à 2 ans = JUNIOR, 3 à 5 ans = INTERMEDIATE, plus de 5 ans = SENIOR.
 */
public enum ExperienceLevel {
    JUNIOR,
    INTERMEDIATE,
    SENIOR;

    public static ExperienceLevel fromYears(int years) {
        if (years <= 2) {
            return JUNIOR;
        }
        return years <= 5 ? INTERMEDIATE : SENIOR;
    }
}
