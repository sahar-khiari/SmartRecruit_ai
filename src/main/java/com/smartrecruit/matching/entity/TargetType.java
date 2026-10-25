package com.smartrecruit.matching.entity;

/** Sens d'une recommandation de matching. */
public enum TargetType {
    /** La recommandation est adressée à un candidat (on lui suggère des offres). */
    CANDIDATE,
    /** La recommandation est adressée à une offre (on suggère des candidats). */
    OFFER
}
