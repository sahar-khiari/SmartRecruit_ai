package com.smartrecruit.matching.entity;

/**
 * Moteur de calcul du score de matching.
 * Indique quelle couche a produit le résultat.
 */
public enum EngineMode {
    /** Couche 0 : TF-IDF + dictionnaire de compétences (toujours disponible). */
    JAVA,
    /** Couche 1 : embeddings sémantiques via FastAPI. */
    EMBEDDINGS,
    /** Couche 2 : LLM-as-judge via FastAPI + LLM configurable. */
    LLM
}
