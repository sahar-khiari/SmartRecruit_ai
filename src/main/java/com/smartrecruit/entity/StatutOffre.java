package com.smartrecruit.entity;

/**
 * Cycle de vie d'une offre d'emploi (Module 2 - Gestion des offres).
 * <ul>
 *   <li>BROUILLON : visible uniquement par son recruteur.</li>
 *   <li>PUBLIEE : visible par les candidats, candidatures ouvertes.</li>
 *   <li>CLOTUREE : recrutement terminé, plus visible par les candidats.</li>
 *   <li>ARCHIVEE : conservée pour l'historique, plus modifiable.</li>
 *   <li>SUSPENDUE : bloquée par un administrateur (modération), avec un motif.</li>
 * </ul>
 */
public enum StatutOffre {
    BROUILLON,
    PUBLIEE,
    CLOTUREE,
    ARCHIVEE,
    SUSPENDUE
}
