import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { KnowledgeDocumentResponse } from '../models/hr/knowledge-document.model';
import { KnowledgeBaseService } from './knowledge-base.service';

const DOCUMENT: KnowledgeDocumentResponse = {
  id: 7,
  title: 'Comment postuler ?',
  content: 'Connectez-vous puis cliquez sur Postuler.',
  category: 'CANDIDATE_GUIDE',
  active: true,
  createdById: 1,
  createdAt: '2026-10-10T10:00:00',
  updatedAt: '2026-10-11T11:00:00',
  version: 0,
  indexingStatus: 'INDEXED',
  indexedAt: '2026-10-11T11:05:00',
  indexingError: null,
};

const PAGE = {
  content: [DOCUMENT],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  last: true,
};

describe('KnowledgeBaseService', () => {
  let service: KnowledgeBaseService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(KnowledgeBaseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lit les documents actifs via l’endpoint utilisateur', () => {
    service
      .getActiveDocuments({ page: 0, size: 10, sort: 'title,asc', search: 'postuler' })
      .subscribe((response) => expect(response.content).toEqual([DOCUMENT]));

    const request = httpMock.expectOne((candidate) =>
      candidate.url === `${environment.apiUrl}/hr/knowledge-documents`,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('10');
    expect(request.request.params.get('sort')).toBe('title,asc');
    expect(request.request.params.get('search')).toBe('postuler');
    expect(request.request.params.has('active')).toBe(false);
    request.flush(PAGE);
  });

  it('transmet tous les filtres à l’endpoint ADMIN', () => {
    service
      .getAdminDocuments({
        page: 1,
        size: 5,
        sort: 'updatedAt,desc',
        category: 'FAQ',
        active: false,
        search: '  congés  ',
      })
      .subscribe();

    const request = httpMock.expectOne((candidate) =>
      candidate.url === `${environment.apiUrl}/admin/hr/knowledge-documents`,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('5');
    expect(request.request.params.get('sort')).toBe('updatedAt,desc');
    expect(request.request.params.get('category')).toBe('FAQ');
    expect(request.request.params.get('active')).toBe('false');
    expect(request.request.params.get('search')).toBe('congés');
    request.flush({ ...PAGE, content: [] });
  });

  it('charge le détail ADMIN d’un document', () => {
    service.getDocumentById(7).subscribe((response) => expect(response.id).toBe(7));

    const request = httpMock.expectOne(
      `${environment.apiUrl}/admin/hr/knowledge-documents/7`,
    );
    expect(request.request.method).toBe('GET');
    request.flush(DOCUMENT);
  });

  it('crée un document avec le contrat exact du backend', () => {
    service
      .createDocument({
        title: 'Comment postuler ?',
        content: 'Connectez-vous puis cliquez sur Postuler.',
        category: 'CANDIDATE_GUIDE',
      })
      .subscribe((response) => expect(response).toEqual(DOCUMENT));

    const request = httpMock.expectOne(
      `${environment.apiUrl}/admin/hr/knowledge-documents`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      title: 'Comment postuler ?',
      content: 'Connectez-vous puis cliquez sur Postuler.',
      category: 'CANDIDATE_GUIDE',
    });
    expect(request.request.body.active).toBeUndefined();
    expect(request.request.body.createdById).toBeUndefined();
    request.flush(DOCUMENT);
  });

  it('met à jour un document avec PUT', () => {
    const update = {
      title: 'Comment postuler ?',
      content: 'Contenu actualisé.',
      category: 'FAQ' as const,
    };
    service.updateDocument(7, update).subscribe();

    const request = httpMock.expectOne(
      `${environment.apiUrl}/admin/hr/knowledge-documents/7`,
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(update);
    request.flush({ ...DOCUMENT, ...update });
  });

  it('active ou désactive via PUT /status avec le champ active', () => {
    service.updateDocumentStatus(7, false).subscribe((response) => {
      expect(response.active).toBe(false);
    });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/admin/hr/knowledge-documents/7/status`,
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ active: false });
    request.flush({ ...DOCUMENT, active: false });
  });

  it('supprime un document avec DELETE', () => {
    service.deleteDocument(7).subscribe();

    const request = httpMock.expectOne(
      `${environment.apiUrl}/admin/hr/knowledge-documents/7`,
    );
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('réindexe une ressource via POST /reindex', () => {
    service.reindexDocument(7).subscribe((response) => {
      expect(response.indexingStatus).toBe('INDEXED');
    });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/admin/hr/knowledge-documents/7/reindex`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(DOCUMENT);
  });

  it('réindexe toutes les ressources actives', () => {
    const bulk = {
      total: 2,
      indexed: 2,
      failed: 0,
      items: [
        {
          documentId: 7,
          indexingStatus: 'INDEXED' as const,
          indexedAt: '2026-10-11T11:05:00',
          indexingError: null,
        },
      ],
    };
    service.reindexAllDocuments().subscribe((response) => {
      expect(response.indexed).toBe(2);
    });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/admin/hr/knowledge-documents/reindex-all`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(bulk);
  });

  it('lit l’état du service d’indexation', () => {
    service.getIndexingHealth().subscribe((response) => {
      expect(response.chromaAvailable).toBe(true);
    });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/admin/hr/knowledge-documents/indexing/health`,
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      status: 'UP',
      service: 'smartrecruit-ai-service',
      version: '1.0.0',
      embeddingModel: 'fake-model',
      chromaAvailable: true,
      timestamp: '2026-10-24T10:00:00Z',
    });
  });

  it('envoie une recherche sémantique ADMIN sans clé interne', () => {
    service
      .searchSemantic({
        query: 'Comment recruter ?',
        topK: 5,
        categories: ['RECRUITMENT_PROCEDURE'],
      })
      .subscribe((response) => {
        expect(response.results[0].chunkId).toBe('document-12-v1-chunk-0');
        expect(response.results[0].score).toBe(0.87);
      });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/admin/hr/retrieval/search`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      query: 'Comment recruter ?',
      topK: 5,
      categories: ['RECRUITMENT_PROCEDURE'],
    });
    expect(JSON.stringify(request.request.body)).not.toContain('X-AI-Service-Key');
    request.flush({
      query: 'Comment recruter ?',
      sanitizedQuery: 'Comment recruter ?',
      results: [
        {
          documentId: 12,
          chunkId: 'document-12-v1-chunk-0',
          chunkIndex: 0,
          title: 'Processus de recrutement',
          category: 'RECRUITMENT_PROCEDURE',
          content: 'Le processus comporte un entretien.',
          score: 0.87,
          version: 1,
        },
      ],
      totalResults: 1,
      modelName: 'fake-model',
      durationMs: 12,
    });
  });
});
