import type { MessageSourceResponse } from './message-source.model';

/**
 * Rôles renvoyés par le backend. Le client ne choisit jamais le rôle
 * d'un message envoyé : Spring impose USER.
 */
export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM';

export interface MessageCreateRequest {
  content: string;
}

export interface HrMessageResponse {
  id: number;
  conversationId: number;
  role: MessageRole;
  content: string;
  createdAt: string;
  sources: MessageSourceResponse[];
}
