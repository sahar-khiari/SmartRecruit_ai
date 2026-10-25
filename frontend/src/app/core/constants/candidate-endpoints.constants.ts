/**
 * Module 1 - endpoints Spring de la gestion des candidats, relatifs à `environment.apiUrl`.
 * Fichier séparé de api-endpoints.constants.ts pour ne pas modifier le fichier commun.
 */
export const CANDIDATE_ENDPOINTS = {
  me: '/candidates/me',
  mySkills: '/candidates/me/skills',
  myCv: '/candidates/me/cv',
  analyze: '/candidates/me/cv/analyze',
  analysis: '/candidates/me/cv/analysis',
  applyAnalysis: '/candidates/me/cv/analysis/apply',
  list: '/candidates',
  byId: (id: number | string) => `/candidates/${id}`,
  cvFile: (id: number | string) => `/candidates/${id}/cv/file`,
  admin: {
    list: '/admin/candidates',
    byId: (id: number | string) => `/admin/candidates/${id}`,
    skills: (id: number | string) => `/admin/candidates/${id}/skills`,
  },
} as const;
