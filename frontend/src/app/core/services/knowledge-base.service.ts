import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../constants/api-endpoints.constants';
import { SUPPRESS_ERROR_NOTIFICATION } from '../interceptors/error-notification.context';
import { KnowledgeCategory } from '../models/hr/knowledge-category.model';
import {
  BulkKnowledgeReindexResponse,
  IndexingHealthResponse,
  KnowledgeDocumentCreateRequest,
  KnowledgeDocumentResponse,
  KnowledgeDocumentStatusRequest,
  KnowledgeDocumentUpdateRequest,
  RetrievalSearchRequest,
  RetrievalSearchResponse,
} from '../models/hr/knowledge-document.model';
import { PageResponse } from '../models/page.model';

export interface KnowledgeDocumentQuery {
  page?: number;
  size?: number;
  sort?: string;
  category?: KnowledgeCategory;
  active?: boolean;
  search?: string;
}

/**
 * Base de connaissances RH.
 * Les lectures utilisateur ne renvoient que les documents actifs.
 * Les écritures ADMIN sont protégées côté backend.
 */
@Injectable({ providedIn: 'root' })
export class KnowledgeBaseService {
  private readonly http = inject(HttpClient);

  getActiveDocuments(
    query: Omit<KnowledgeDocumentQuery, 'active'> = {},
    silent = true,
  ): Observable<PageResponse<KnowledgeDocumentResponse>> {
    return this.http.get<PageResponse<KnowledgeDocumentResponse>>(
      `${environment.apiUrl}${API_ENDPOINTS.hr.knowledgeDocuments}`,
      { params: this.buildParams(query), ...this.requestOptions(silent) },
    );
  }

  getAdminDocuments(
    query: KnowledgeDocumentQuery = {},
    silent = true,
  ): Observable<PageResponse<KnowledgeDocumentResponse>> {
    return this.http.get<PageResponse<KnowledgeDocumentResponse>>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.knowledgeDocuments}`,
      { params: this.buildParams(query), ...this.requestOptions(silent) },
    );
  }

  getDocumentById(id: number, silent = true): Observable<KnowledgeDocumentResponse> {
    return this.http.get<KnowledgeDocumentResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.knowledgeDocumentById(id)}`,
      this.requestOptions(silent),
    );
  }

  createDocument(
    request: KnowledgeDocumentCreateRequest,
    silent = true,
  ): Observable<KnowledgeDocumentResponse> {
    return this.http.post<KnowledgeDocumentResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.knowledgeDocuments}`,
      request,
      this.requestOptions(silent),
    );
  }

  updateDocument(
    id: number,
    request: KnowledgeDocumentUpdateRequest,
    silent = true,
  ): Observable<KnowledgeDocumentResponse> {
    return this.http.put<KnowledgeDocumentResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.knowledgeDocumentById(id)}`,
      request,
      this.requestOptions(silent),
    );
  }

  updateDocumentStatus(
    id: number,
    active: boolean,
    silent = true,
  ): Observable<KnowledgeDocumentResponse> {
    return this.http.put<KnowledgeDocumentResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.knowledgeDocumentStatus(id)}`,
      { active } satisfies KnowledgeDocumentStatusRequest,
      this.requestOptions(silent),
    );
  }

  deleteDocument(id: number, silent = true): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.knowledgeDocumentById(id)}`,
      this.requestOptions(silent),
    );
  }

  reindexDocument(id: number, silent = true): Observable<KnowledgeDocumentResponse> {
    return this.http.post<KnowledgeDocumentResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.knowledgeDocumentReindex(id)}`,
      {},
      this.requestOptions(silent),
    );
  }

  reindexAllDocuments(silent = true): Observable<BulkKnowledgeReindexResponse> {
    return this.http.post<BulkKnowledgeReindexResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.knowledgeDocumentsReindexAll}`,
      {},
      this.requestOptions(silent),
    );
  }

  getIndexingHealth(silent = true): Observable<IndexingHealthResponse> {
    return this.http.get<IndexingHealthResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.knowledgeIndexingHealth}`,
      this.requestOptions(silent),
    );
  }

  searchSemantic(
    request: RetrievalSearchRequest,
    silent = true,
  ): Observable<RetrievalSearchResponse> {
    return this.http.post<RetrievalSearchResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.retrievalSearch}`,
      request,
      this.requestOptions(silent),
    );
  }

  private buildParams(query: KnowledgeDocumentQuery): HttpParams {
    let params = new HttpParams()
      .set('page', query.page ?? 0)
      .set('size', query.size ?? 20)
      .set('sort', query.sort ?? 'updatedAt,desc');

    if (query.category) {
      params = params.set('category', query.category);
    }
    if (query.active !== undefined) {
      params = params.set('active', query.active);
    }
    if (query.search?.trim()) {
      params = params.set('search', query.search.trim());
    }
    return params;
  }

  private requestOptions(silent: boolean): { context?: HttpContext } {
    return silent
      ? { context: new HttpContext().set(SUPPRESS_ERROR_NOTIFICATION, true) }
      : {};
  }
}
