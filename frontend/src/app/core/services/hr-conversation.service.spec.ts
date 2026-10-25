import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { ConversationResponse } from '../models/hr/conversation.model';
import { HrMessageResponse } from '../models/hr/hr-message.model';
import { HrConversationService } from './hr-conversation.service';

const CONVERSATION: ConversationResponse = {
  id: 12,
  title: 'Préparation entretien',
  createdAt: '2026-10-12T09:00:00',
  updatedAt: '2026-10-12T09:30:00',
};

const MESSAGE: HrMessageResponse = {
  id: 44,
  conversationId: 12,
  role: 'USER',
  content: 'Comment préparer un entretien Java ?',
  createdAt: '2026-10-12T09:31:00',
  sources: [],
};

describe('HrConversationService', () => {
  let service: HrConversationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(HrConversationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('crée une conversation sur POST /api/hr/conversations', () => {
    service.createConversation({ title: 'Nouvelle conversation' }).subscribe((response) => {
      expect(response).toEqual(CONVERSATION);
    });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/hr/conversations`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ title: 'Nouvelle conversation' });
    expect(request.request.body.userId).toBeUndefined();
    request.flush(CONVERSATION);
  });

  it('liste les conversations avec pagination et tri', () => {
    service.getConversations(1, 25, 'createdAt,asc').subscribe((response) => {
      expect(response.content).toEqual([CONVERSATION]);
      expect(response.totalElements).toBe(1);
    });

    const request = httpMock.expectOne((candidate) =>
      candidate.url === `${environment.apiUrl}/hr/conversations`,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.get('sort')).toBe('createdAt,asc');
    request.flush({
      content: [CONVERSATION],
      page: 1,
      size: 25,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });
  });

  it('charge une conversation par son identifiant', () => {
    service.getConversation(12).subscribe((response) => expect(response.id).toBe(12));

    const request = httpMock.expectOne(`${environment.apiUrl}/hr/conversations/12`);
    expect(request.request.method).toBe('GET');
    request.flush(CONVERSATION);
  });

  it('renomme une conversation avec PUT', () => {
    const renamed = { ...CONVERSATION, title: 'Questions RH' };
    service.renameConversation(12, { title: 'Questions RH' }).subscribe((response) => {
      expect(response.title).toBe('Questions RH');
    });

    const request = httpMock.expectOne(`${environment.apiUrl}/hr/conversations/12`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ title: 'Questions RH' });
    request.flush(renamed);
  });

  it('supprime une conversation avec DELETE', () => {
    service.deleteConversation(12).subscribe();

    const request = httpMock.expectOne(`${environment.apiUrl}/hr/conversations/12`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('charge les messages dans l’ordre fourni par le backend', () => {
    service.getMessages(12).subscribe((messages) => {
      expect(messages).toEqual([MESSAGE]);
    });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/hr/conversations/12/messages`,
    );
    expect(request.request.method).toBe('GET');
    request.flush([MESSAGE]);
  });

  it('envoie une question sur POST /api/hr/conversations/{id}/ask', () => {
    const response = {
      userMessage: MESSAGE,
      assistantMessage: {
        id: 45,
        conversationId: 12,
        role: 'ASSISTANT' as const,
        content: 'Consultez les offres depuis votre espace candidat.',
        createdAt: '2026-10-12T09:32:00',
        sources: [
          {
            documentId: 10,
            chunkId: 'document-10-v1-chunk-0',
            chunkIndex: 0,
            title: 'Comment postuler à une offre',
            category: 'PLATFORM_HELP' as const,
            score: 0.74,
            documentVersion: 1,
            excerpt: 'Pour postuler, consultez les offres…',
          },
        ],
      },
      sources: [],
      grounded: true,
      needsHumanReview: false,
      modelName: 'qwen3:4b',
      promptVersion: 'hr-rag-v1',
      durationMs: 1234,
    };

    service
      .ask(12, { content: 'Comment postuler à une offre ?' })
      .subscribe((body) => {
        expect(body.grounded).toBe(true);
        expect(body.assistantMessage.sources[0].score).toBe(0.74);
      });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/hr/conversations/12/ask`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      content: 'Comment postuler à une offre ?',
    });
    expect(request.request.body.role).toBeUndefined();
    expect(request.request.body.userId).toBeUndefined();
    expect(request.request.body.modelName).toBeUndefined();
    request.flush(response);
  });

  it('envoie uniquement le contenu du message, sans rôle ni userId', () => {
    service
      .sendMessage(12, { content: 'Comment préparer un entretien Java ?' })
      .subscribe((message) => {
        expect(message.role).toBe('USER');
        expect(message.content).toBe('Comment préparer un entretien Java ?');
      });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/hr/conversations/12/messages`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      content: 'Comment préparer un entretien Java ?',
    });
    expect(request.request.body.role).toBeUndefined();
    expect(request.request.body.userId).toBeUndefined();
    request.flush(MESSAGE);
  });
});
