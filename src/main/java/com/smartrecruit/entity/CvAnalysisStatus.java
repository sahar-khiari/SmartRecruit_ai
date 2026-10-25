package com.smartrecruit.entity;

/**
 * État d'une analyse IA de CV : créée (PENDING) avant l'appel au service IA, puis terminée ou en échec.
 */
public enum CvAnalysisStatus {
    PENDING,
    COMPLETED,
    FAILED
}
