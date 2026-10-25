import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../constants/api-endpoints.constants';
import { SUPPRESS_ERROR_NOTIFICATION } from '../interceptors/error-notification.context';
import {
  ConversationCreateRequest,
  ConversationResponse,
  ConversationUpdateRequest,
} from '../models/hr/conversation.model';
import {
  HrMessageResponse,
  MessageCreateRequest,
} from '../models/hr/hr-message.model';
import { HrAskRequest, HrAskResponse } from '../models/hr/rag-chat.model';
import { PageResponse } from '../models/page.model';

/**
 * Conversations privées de l'Assistant RH.
 * Aucun userId n'est envoyé : l'identité provient du JWT via l'intercepteur.
 */
@Injectable({ providedIn: 'root' })
export class HrConversationService {
  private readonly http = inject(HttpClient);

  createConversation(
    request: ConversationCreateRequest = {},
    silent = true,
  ): Observable<ConversationResponse> {
    return this.http.post<ConversationResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.hr.conversations}`,
      request,
      this.requestOptions(silent),
    );
  }

  getConversations(
    page = 0,
    size = 100,
    sort = 'updatedAt,desc',
    silent = true,
  ): Observable<PageResponse<ConversationResponse>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    return this.http.get<PageResponse<ConversationResponse>>(
      `${environment.apiUrl}${API_ENDPOINTS.hr.conversations}`,
      { params, ...this.requestOptions(silent) },
    );
  }

  getConversation(id: number, silent = true): Observable<ConversationResponse> {
    return this.http.get<ConversationResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.hr.conversationById(id)}`,
      this.requestOptions(silent),
    );
  }

  renameConversation(
    id: number,
    request: ConversationUpdateRequest,
    silent = true,
  ): Observable<ConversationResponse> {
    return this.http.put<ConversationResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.hr.conversationById(id)}`,
      request,
      this.requestOptions(silent),
    );
  }

  deleteConversation(id: number, silent = true): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiUrl}${API_ENDPOINTS.hr.conversationById(id)}`,
      this.requestOptions(silent),
    );
  }

  getMessages(conversationId: number, silent = true): Observable<HrMessageResponse[]> {
    return this.http.get<HrMessageResponse[]>(
      `${environment.apiUrl}${API_ENDPOINTS.hr.messages(conversationId)}`,
      this.requestOptions(silent),
    );
  }

  /**
   * Envoie une question au pipeline RAG via Spring Boot.
   * Angular n'appelle jamais FastAPI ni Ollama et ne reçoit jamais la clé interne.
   */
  ask(
    conversationId: number,
    request: HrAskRequest,
    silent = true,
  ): Observable<HrAskResponse> {
    return this.http.post<HrAskResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.hr.ask(conversationId)}`,
      request,
      this.requestOptions(silent),
    );
  }

  /**
   * Endpoint de compatibilité sans génération, conservé pour les clients
   * existants. L'interface utilise exclusivement {@link ask}.
   */
  sendMessage(
    conversationId: number,
    request: MessageCreateRequest,
    silent = true,
  ): Observable<HrMessageResponse> {
    return this.http.post<HrMessageResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.hr.messages(conversationId)}`,
      request,
      this.requestOptions(silent),
    );
  }

  private requestOptions(silent: boolean): { context?: HttpContext } {
    return silent
      ? { context: new HttpContext().set(SUPPRESS_ERROR_NOTIFICATION, true) }
      : {};
  }
}
