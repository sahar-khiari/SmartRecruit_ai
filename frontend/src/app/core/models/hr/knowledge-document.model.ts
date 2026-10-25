import { KnowledgeCategory } from './knowledge-category.model';

export type IndexingStatus = 'PENDING' | 'INDEXED' | 'FAILED' | 'NOT_INDEXED';

export interface KnowledgeDocumentCreateRequest {
  title: string;
  content: string;
  category: KnowledgeCategory;
}

export interface KnowledgeDocumentUpdateRequest {
  title: string;
  content: string;
  category: KnowledgeCategory;
}

export interface KnowledgeDocumentStatusRequest {
  active: boolean;
}

export interface KnowledgeDocumentResponse {
  id: number;
  title: string;
  content: string;
  category: KnowledgeCategory;
  active: boolean;
  createdById: number | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  indexingStatus: IndexingStatus;
  indexedAt: string | null;
  indexingError: string | null;
}

export interface KnowledgeDocumentReindexItem {
  documentId: number;
  indexingStatus: IndexingStatus;
  indexedAt: string | null;
  indexingError: string | null;
}

export interface BulkKnowledgeReindexResponse {
  total: number;
  indexed: number;
  failed: number;
  items: KnowledgeDocumentReindexItem[];
}

export interface IndexingHealthResponse {
  status: string;
  service: string;
  version: string;
  embeddingModel: string;
  chromaAvailable: boolean;
  timestamp: string;
}

export interface RetrievalSearchRequest {
  query: string;
  topK?: number;
  categories?: KnowledgeCategory[];
}

export interface RetrievedChunk {
  documentId: number;
  chunkId: string;
  chunkIndex: number;
  title: string;
  category: KnowledgeCategory;
  content: string;
  score: number;
  version: number;
}

export interface RetrievalSearchResponse {
  query: string;
  sanitizedQuery: string;
  results: RetrievedChunk[];
  totalResults: number;
  modelName: string;
  durationMs: number;
}
