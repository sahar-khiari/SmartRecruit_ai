/**
 * Modèles TypeScript du module matching.
 * Correspondent exactement aux DTOs Spring Boot.
 */

export type EngineMode = 'JAVA' | 'EMBEDDINGS' | 'LLM';
export type Verdict = 'STRONG_MATCH' | 'POSSIBLE' | 'WEAK';
export type TargetType = 'CANDIDATE' | 'OFFER';
export type StatutRecommandation = 'EN_ATTENTE' | 'ACCEPTEE' | 'REJETEE';

export interface MatchingStatus {
  layer0Available: boolean;
  layer1Available: boolean;
  layer2Available: boolean;
  activeEngine: EngineMode;
  fastApiStatus: string;
  llmEnabled: boolean;
  llmModel: string;
  llmBaseUrl: string;
}

export interface CandidatProfile {
  id: number;
  userId: number;
  cvTextPreview: string;
  skills: string[];
  niveau: string;
  resumeProfessionnel: string;
  updatedAt: string;
}

export interface CandidatProfileRequest {
  cvText?: string;
  niveau?: string;
  resumeProfessionnel?: string;
}

export interface MatchingCritere {
  id: number;
  offreId: number;
  nom: string;
  poids: number;
  obligatoire: boolean;
}

export interface MatchingCritereRequest {
  offreId: number;
  nom: string;
  poids: number;
  obligatoire: boolean;
}

export interface MatchingScore {
  id: number;
  candidateId: number;
  candidatNom: string;
  candidatEmail: string;
  offreId: number;
  offreTitre: string;
  finalScore: number;
  semanticScore: number;
  llmScore: number | null;
  testScore: number | null;
  experienceScore: number;
  verdict: Verdict;
  explication: string;
  engineMode: EngineMode;
  promptVersion: string | null;
  matchedSkills: string[];
  missingSkills: string[];
  breakdown: Record<string, number> | null;
  createdAt: string;
}

export interface MatchingRecommandation {
  id: number;
  targetType: TargetType;
  targetId: number;
  itemId: number;
  itemLabel: string;
  rang: number;
  score: number;
  raison: string;
  statut: StatutRecommandation;
  feedback: string | null;
  createdAt: string;
}

export interface FeedbackRequest {
  recommandationId: number;
  action: 'ACCEPTEE' | 'REJETEE';
  commentaire?: string;
}
