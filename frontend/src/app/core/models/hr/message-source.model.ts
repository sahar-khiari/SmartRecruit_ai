import { KnowledgeCategory } from './knowledge-category.model';

/**
 * Source documentaire renvoyée avec une réponse ASSISTANT.
 * Le score est une similarité vectorielle, jamais une probabilité de vérité.
 */
export interface MessageSourceResponse {
  documentId: number;
  chunkId: string;
  chunkIndex: number;
  title: string;
  category: KnowledgeCategory;
  score: number;
  documentVersion: number;
  excerpt?: string | null;
}
