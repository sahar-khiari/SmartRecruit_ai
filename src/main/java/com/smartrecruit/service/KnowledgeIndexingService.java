package com.smartrecruit.service;

import com.smartrecruit.dto.ai.AiKnowledgeIndexRequest;
import com.smartrecruit.dto.ai.AiKnowledgeIndexResponse;
import com.smartrecruit.dto.internal.KnowledgeDocumentIndexingSnapshot;
import com.smartrecruit.entity.IndexingStatus;
import com.smartrecruit.integration.ai.AiServiceClient;
import com.smartrecruit.integration.ai.AiServiceException;
import com.smartrecruit.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Orchestration de l'indexation vectorielle. Les appels HTTP sont effectués
 * hors des transactions de persistance longues. Un échec FastAPI marque le
 * document FAILED sans jamais supprimer le document métier.
 */
@Service
public class KnowledgeIndexingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingService.class);
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final String INVALID_RESPONSE =
            "Réponse d'indexation invalide. Le document reste disponible dans MariaDB.";
    private static final String UNEXPECTED_ERROR =
            "Échec de l'indexation vectorielle. Le document reste disponible dans MariaDB.";
    private static final String DELETE_ERROR =
            "La suppression vectorielle a échoué. Le document reste disponible dans MariaDB.";

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final AiServiceClient aiServiceClient;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;

    public KnowledgeIndexingService(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            AiServiceClient aiServiceClient,
            PlatformTransactionManager transactionManager,
            @Value("${app.ai-service.enabled:true}") boolean enabled) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.aiServiceClient = aiServiceClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
    }

    /**
     * Synchronise un document. Retourne le statut final enregistré.
     */
    public IndexingStatus index(KnowledgeDocumentIndexingSnapshot snapshot) {
        if (!snapshot.active()) {
            return removeFromIndex(snapshot.id());
        }

        if (!enabled) {
            // Profil de test ou maintenance : aucune dépendance réseau.
            log.debug("Indexation désactivée pour le document {}.", snapshot.id());
            return IndexingStatus.PENDING;
        }

        markPending(snapshot.id());
        try {
            AiKnowledgeIndexResponse response = aiServiceClient.indexDocument(toRequest(snapshot));
            if (response == null
                    || !"INDEXED".equals(response.status())
                    || response.chunksIndexed() <= 0) {
                return markFailed(snapshot.id(), INVALID_RESPONSE);
            }
            markIndexed(snapshot.id());
            return IndexingStatus.INDEXED;
        } catch (AiServiceException ex) {
            return markFailed(snapshot.id(), safeMessage(ex.getMessage(), UNEXPECTED_ERROR));
        } catch (Exception ex) {
            log.warn("Erreur inattendue d'indexation pour le document {} ({})",
                    snapshot.id(), ex.getClass().getSimpleName());
            return markFailed(snapshot.id(), UNEXPECTED_ERROR);
        }
    }

    /**
     * Suppression vectorielle idempotente. Utilisée pour la désactivation et la
     * suppression métier. Un échec est journalisé mais ne bloque jamais MariaDB.
     */
    public IndexingStatus removeFromIndex(Long documentId) {
        if (!enabled) {
            markNotIndexed(documentId);
            return IndexingStatus.NOT_INDEXED;
        }

        try {
            markPending(documentId);
            aiServiceClient.deleteDocument(documentId);
            markNotIndexed(documentId);
            return IndexingStatus.NOT_INDEXED;
        } catch (AiServiceException ex) {
            return markFailed(documentId, safeMessage(ex.getMessage(), DELETE_ERROR));
        } catch (Exception ex) {
            log.warn("Erreur inattendue de suppression vectorielle pour le document {} ({})",
                    documentId, ex.getClass().getSimpleName());
            return markFailed(documentId, DELETE_ERROR);
        }
    }

    /**
     * Suppression best-effort des vecteurs lors d'une suppression métier.
     * MariaDB doit rester utilisable même si FastAPI est arrêté.
     */
    public void deleteVectorsBestEffort(Long documentId) {
        if (!enabled) {
            return;
        }
        try {
            aiServiceClient.deleteDocument(documentId);
        } catch (Exception ex) {
            log.warn("Chunks non supprimés pour le document {} : service d'indexation indisponible.",
                    documentId);
        }
    }

    private AiKnowledgeIndexRequest toRequest(KnowledgeDocumentIndexingSnapshot snapshot) {
        return new AiKnowledgeIndexRequest(
                snapshot.id(),
                snapshot.title(),
                snapshot.content(),
                snapshot.category().name(),
                snapshot.version(),
                snapshot.active()
        );
    }

    private void markPending(Long documentId) {
        transactionTemplate.executeWithoutResult(status ->
                knowledgeDocumentRepository.updateIndexingState(
                        documentId, IndexingStatus.PENDING, null, null));
    }

    private void markIndexed(Long documentId) {
        transactionTemplate.executeWithoutResult(status ->
                knowledgeDocumentRepository.updateIndexingState(
                        documentId, IndexingStatus.INDEXED, LocalDateTime.now(), null));
    }

    private void markNotIndexed(Long documentId) {
        transactionTemplate.executeWithoutResult(status ->
                knowledgeDocumentRepository.updateIndexingState(
                        documentId, IndexingStatus.NOT_INDEXED, null, null));
    }

    private IndexingStatus markFailed(Long documentId, String message) {
        String safeMessage = safeMessage(message, UNEXPECTED_ERROR);
        transactionTemplate.executeWithoutResult(status ->
                knowledgeDocumentRepository.updateIndexingState(
                        documentId, IndexingStatus.FAILED, null, safeMessage));
        return IndexingStatus.FAILED;
    }

    private String safeMessage(String message, String fallback) {
        String value = message == null || message.isBlank() ? fallback : message.trim();
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
