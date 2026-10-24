/**
 * Contrats des conversations de l'Assistant RH.
 * Le backend détermine toujours le propriétaire à partir du JWT.
 */
export interface ConversationCreateRequest {
  title?: string;
}

export interface ConversationUpdateRequest {
  title: string;
}

export interface ConversationResponse {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}
