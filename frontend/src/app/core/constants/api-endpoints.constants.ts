/**
 * Chemins des endpoints du backend, relatifs à `environment.apiUrl`.
 * Ne jamais répéter l'URL complète dans les services.
 */
export const API_ENDPOINTS = {
  auth: {
    signup: '/auth/signup',
    login: '/auth/login',
    logout: '/auth/logout',
  },
  users: {
    me: '/users/me',
    password: '/users/me/password',
  },
  hr: {
    conversations: '/hr/conversations',
    conversationById: (id: number | string) => `/hr/conversations/${id}`,
    messages: (conversationId: number | string) =>
      `/hr/conversations/${conversationId}/messages`,
    ask: (conversationId: number | string) =>
      `/hr/conversations/${conversationId}/ask`,
    knowledgeDocuments: '/hr/knowledge-documents',
  },
  admin: {
    users: '/admin/users',
    userById: (id: number | string) => `/admin/users/${id}`,
    userStatus: (id: number | string) => `/admin/users/${id}/status`,
    userRole: (id: number | string) => `/admin/users/${id}/role`,
    knowledgeDocuments: '/admin/hr/knowledge-documents',
    knowledgeDocumentById: (id: number | string) =>
      `/admin/hr/knowledge-documents/${id}`,
    knowledgeDocumentStatus: (id: number | string) =>
      `/admin/hr/knowledge-documents/${id}/status`,
    knowledgeDocumentReindex: (id: number | string) =>
      `/admin/hr/knowledge-documents/${id}/reindex`,
    knowledgeDocumentsReindexAll: '/admin/hr/knowledge-documents/reindex-all',
    knowledgeIndexingHealth: '/admin/hr/knowledge-documents/indexing/health',
    retrievalSearch: '/admin/hr/retrieval/search',
  },
} as const;
