package com.smartrecruit.service;

import com.smartrecruit.dto.internal.KnowledgeDocumentIndexingSnapshot;
import com.smartrecruit.dto.request.KnowledgeDocumentCreateRequest;
import com.smartrecruit.dto.request.KnowledgeDocumentUpdateRequest;
import com.smartrecruit.dto.response.BulkKnowledgeReindexResponse;
import com.smartrecruit.dto.response.KnowledgeDocumentReindexResponse;
import com.smartrecruit.dto.response.KnowledgeDocumentResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.IndexingStatus;
import com.smartrecruit.entity.KnowledgeCategory;
import com.smartrecruit.entity.KnowledgeDocument;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.KnowledgeDocumentNotFoundException;
import com.smartrecruit.mapper.KnowledgeDocumentMapper;
import com.smartrecruit.repository.KnowledgeDocumentRepository;
import com.smartrecruit.service.KnowledgeIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Service métier de la base de connaissances RH.
 *
 * Les écritures MariaDB sont volontairement découpées en transactions courtes :
 * l'appel FastAPI (nettoyage, chunks, embeddings) est effectué hors transaction.
 * MariaDB reste la source de vérité, même si l'indexation vectorielle échoue.
 */
@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentServiceImpl.class);

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final CurrentUserService currentUserService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeIndexingService knowledgeIndexingService;
    private final TransactionTemplate transactionTemplate;
    private final int reindexAllMax;

    public KnowledgeDocumentServiceImpl(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            CurrentUserService currentUserService,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeIndexingService knowledgeIndexingService,
            PlatformTransactionManager transactionManager,
            @Value("${app.ai-service.reindex-all-max:100}") int reindexAllMax) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.currentUserService = currentUserService;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeIndexingService = knowledgeIndexingService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.reindexAllMax = Math.max(1, reindexAllMax);
    }

    @Override
    public KnowledgeDocumentResponse create(KnowledgeDocumentCreateRequest request) {
        KnowledgeDocumentIndexingSnapshot snapshot = transactionTemplate.execute(status -> {
            User admin = currentUserService.getCurrentUser();
            KnowledgeDocument document = KnowledgeDocument.builder()
                    .title(request.title().trim())
                    .content(request.content())
                    .category(request.category())
                    .active(true)
                    .indexingStatus(IndexingStatus.PENDING)
                    .createdBy(admin)
                    .build();
            knowledgeDocumentRepository.saveAndFlush(document);
            return toSnapshot(document);
        });

        knowledgeIndexingService.index(snapshot);
        return getForAdmin(snapshot.id());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeDocumentResponse> searchForAdmin(KnowledgeCategory category,
                                                                  Boolean active,
                                                                  String search,
                                                                  Pageable pageable) {
        return PageResponse.from(
                knowledgeDocumentRepository.searchForAdmin(category, active, normalizeSearch(search), pageable),
                knowledgeDocumentMapper::toResponse
        );
    }

    @Override
    public KnowledgeDocumentResponse getForAdmin(Long id) {
        return transactionTemplate.execute(status ->
                knowledgeDocumentMapper.toResponse(findDocument(id)));
    }

    @Override
    public KnowledgeDocumentResponse update(Long id, KnowledgeDocumentUpdateRequest request) {
        KnowledgeDocumentIndexingSnapshot snapshot = transactionTemplate.execute(status -> {
            KnowledgeDocument document = findDocument(id);
            document.setTitle(request.title().trim());
            document.setContent(request.content());
            document.setCategory(request.category());
            document.setIndexingStatus(IndexingStatus.PENDING);
            document.setIndexedAt(null);
            document.setIndexingError(null);
            knowledgeDocumentRepository.saveAndFlush(document);
            return toSnapshot(document);
        });

        knowledgeIndexingService.index(snapshot);
        return getForAdmin(id);
    }

    @Override
    public KnowledgeDocumentResponse updateStatus(Long id, boolean active) {
        KnowledgeDocumentIndexingSnapshot snapshot = transactionTemplate.execute(status -> {
            KnowledgeDocument document = findDocument(id);
            document.setActive(active);
            document.setIndexingStatus(IndexingStatus.PENDING);
            document.setIndexedAt(null);
            document.setIndexingError(null);
            knowledgeDocumentRepository.saveAndFlush(document);
            return toSnapshot(document);
        });

        knowledgeIndexingService.index(snapshot);
        return getForAdmin(id);
    }

    @Override
    public void delete(Long id) {
        KnowledgeDocumentIndexingSnapshot snapshot = transactionTemplate.execute(status ->
                toSnapshot(findDocument(id)));

        // Best effort : un FastAPI arrêté ne doit pas empêcher la suppression métier.
        knowledgeIndexingService.deleteVectorsBestEffort(snapshot.id());

        transactionTemplate.executeWithoutResult(status -> {
            KnowledgeDocument document = findDocument(id);
            knowledgeDocumentRepository.delete(document);
        });
    }

    @Override
    public KnowledgeDocumentResponse reindex(Long id) {
        KnowledgeDocumentIndexingSnapshot snapshot = transactionTemplate.execute(status ->
                toSnapshot(findDocument(id)));
        knowledgeIndexingService.index(snapshot);
        return getForAdmin(id);
    }

    @Override
    public BulkKnowledgeReindexResponse reindexAll() {
        List<Long> ids = transactionTemplate.execute(status ->
                knowledgeDocumentRepository.findActiveIds(
                        PageRequest.of(0, reindexAllMax, Sort.by(Sort.Direction.ASC, "id"))));

        int indexed = 0;
        int failed = 0;
        List<KnowledgeDocumentReindexResponse> items = new ArrayList<>();

        for (Long id : ids) {
            try {
                KnowledgeDocumentIndexingSnapshot snapshot = transactionTemplate.execute(status ->
                        toSnapshot(findDocument(id)));
                IndexingStatus status = knowledgeIndexingService.index(snapshot);
                KnowledgeDocumentResponse document = getForAdmin(id);
                items.add(new KnowledgeDocumentReindexResponse(
                        document.id(),
                        document.indexingStatus(),
                        document.indexedAt(),
                        document.indexingError()
                ));
                if (status == IndexingStatus.INDEXED) {
                    indexed++;
                } else {
                    failed++;
                }
            } catch (RuntimeException ex) {
                log.warn("Réindexation globale impossible pour le document {} ({})",
                        id, ex.getClass().getSimpleName());
                failed++;
            }
        }

        return new BulkKnowledgeReindexResponse(ids.size(), indexed, failed, List.copyOf(items));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeDocumentResponse> searchActive(KnowledgeCategory category,
                                                                String search,
                                                                Pageable pageable) {
        return PageResponse.from(
                knowledgeDocumentRepository.searchActive(category, normalizeSearch(search), pageable),
                knowledgeDocumentMapper::toResponse
        );
    }

    private KnowledgeDocument findDocument(Long id) {
        return knowledgeDocumentRepository.findById(id)
                .orElseThrow(() -> new KnowledgeDocumentNotFoundException(id));
    }

    private String normalizeSearch(String search) {
        return StringUtils.hasText(search) ? search.trim() : null;
    }

    private static KnowledgeDocumentIndexingSnapshot toSnapshot(KnowledgeDocument document) {
        return new KnowledgeDocumentIndexingSnapshot(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getCategory(),
                document.getVersion() == null ? 0L : document.getVersion(),
                document.isActive()
        );
    }
}
