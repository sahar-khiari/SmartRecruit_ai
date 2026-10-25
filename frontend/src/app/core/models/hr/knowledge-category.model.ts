export type KnowledgeCategory =
  | 'FAQ'
  | 'CANDIDATE_GUIDE'
  | 'RECRUITER_GUIDE'
  | 'INTERVIEW_GUIDE'
  | 'RECRUITMENT_PROCEDURE'
  | 'PLATFORM_HELP'
  | 'HR_POLICY'
  | 'OTHER';

export interface KnowledgeCategoryOption {
  value: KnowledgeCategory;
  label: string;
}

/**
 * Mapping centralisé : aucun composant ne doit redéfinir ces libellés.
 */
export const KNOWLEDGE_CATEGORY_LABELS: Record<KnowledgeCategory, string> = {
  FAQ: 'FAQ',
  CANDIDATE_GUIDE: 'Guide candidat',
  RECRUITER_GUIDE: 'Guide recruteur',
  INTERVIEW_GUIDE: "Guide d'entretien",
  RECRUITMENT_PROCEDURE: 'Procédure de recrutement',
  PLATFORM_HELP: 'Aide de la plateforme',
  HR_POLICY: 'Politique RH',
  OTHER: 'Autre',
};

export const KNOWLEDGE_CATEGORY_OPTIONS: readonly KnowledgeCategoryOption[] = (
  Object.entries(KNOWLEDGE_CATEGORY_LABELS) as [KnowledgeCategory, string][]
).map(([value, label]) => ({ value, label }));

export function knowledgeCategoryLabel(category: KnowledgeCategory): string {
  return KNOWLEDGE_CATEGORY_LABELS[category] ?? category;
}
