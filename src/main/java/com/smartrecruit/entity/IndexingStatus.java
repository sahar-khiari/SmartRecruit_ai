package com.smartrecruit.entity;

/**
 * État de synchronisation entre MariaDB et l'index vectoriel ChromaDB.
 * MariaDB reste la source de vérité métier.
 */
public enum IndexingStatus {
    PENDING,
    INDEXED,
    FAILED,
    NOT_INDEXED
}
