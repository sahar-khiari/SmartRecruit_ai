package com.smartrecruit.service;

import com.smartrecruit.dto.request.KnowledgeDocumentCreateRequest;
import com.smartrecruit.dto.request.KnowledgeDocumentUpdateRequest;
import com.smartrecruit.dto.response.BulkKnowledgeReindexResponse;
import com.smartrecruit.dto.response.KnowledgeDocumentResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.KnowledgeCategory;
import org.springframework.data.domain.Pageable;

public interface KnowledgeDocumentService {

    KnowledgeDocumentResponse create(KnowledgeDocumentCreateRequest request);

    PageResponse<KnowledgeDocumentResponse> searchForAdmin(KnowledgeCategory category,
                                                           Boolean active,
                                                           String search,
                                                           Pageable pageable);

    KnowledgeDocumentResponse getForAdmin(Long id);

    KnowledgeDocumentResponse update(Long id, KnowledgeDocumentUpdateRequest request);

    KnowledgeDocumentResponse updateStatus(Long id, boolean active);

    void delete(Long id);

    KnowledgeDocumentResponse reindex(Long id);

    BulkKnowledgeReindexResponse reindexAll();

    PageResponse<KnowledgeDocumentResponse> searchActive(KnowledgeCategory category,
                                                         String search,
                                                         Pageable pageable);
}
