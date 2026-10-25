/**
 * Module 1 - Gestion des candidats : types alignés sur les DTO Spring
 * (CandidateProfileResponse, CandidateCvResponse, CvAnalysisResponse...).
 */

export type ExperienceLevel = 'JUNIOR' | 'INTERMEDIATE' | 'SENIOR';
export type SkillSource = 'MANUAL' | 'AI';
export type CvAnalysisStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export const EXPERIENCE_LEVEL_LABELS: Record<ExperienceLevel, string> = {
  JUNIOR: 'Junior (0 à 2 ans)',
  INTERMEDIATE: 'Intermédiaire (3 à 5 ans)',
  SENIOR: 'Senior (6 ans et plus)',
};

export interface CandidateSkill {
  id: number;
  name: string;
  source: SkillSource;
}

export interface CandidateProfile {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  enabled: boolean;
  profileCompleted: boolean;
  headline: string | null;
  phone: string | null;
  city: string | null;
  githubUrl: string | null;
  linkedinUrl: string | null;
  summary: string | null;
  experienceLevel: ExperienceLevel | null;
  yearsOfExperience: number | null;
  skills: CandidateSkill[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface CandidateSummary {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  enabled: boolean;
  headline: string | null;
  city: string | null;
  experienceLevel: ExperienceLevel | null;
  yearsOfExperience: number | null;
  skills: string[];
}

export interface CandidateProfileUpdateRequest {
  headline: string | null;
  phone: string | null;
  city: string | null;
  githubUrl: string | null;
  linkedinUrl: string | null;
  summary: string | null;
  experienceLevel: ExperienceLevel | null;
  yearsOfExperience: number | null;
}

export interface CandidateCv {
  id: number;
  candidateId: number;
  originalFilename: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface ProposedSkill {
  /** Telle qu'écrite dans le CV. */
  original: string;
  /** Nom normalisé (référentiel). */
  name: string;
  category: string | null;
  method: 'EXACT' | 'VERSION' | 'FUZZY' | 'UNKNOWN';
}

export interface CvAnalysisProposal {
  headline: string | null;
  summary: string | null;
  yearsOfExperience: number | null;
  experienceLevel: ExperienceLevel | null;
  skills: ProposedSkill[];
  softSkills: string[];
  languages: string[];
}

export interface CvAnalysisTrace {
  model: string | null;
  fallbackUsed: boolean | null;
  promptVersion: string | null;
  pipelineVersion: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  durationMs: number | null;
}

export interface CvAnalysis {
  id: number;
  cvId: number;
  status: CvAnalysisStatus;
  proposal: CvAnalysisProposal | null;
  trace: CvAnalysisTrace;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
  appliedAt: string | null;
}

export interface CvAnalysisApplyRequest {
  analysisId: number;
  headline: string | null;
  summary: string | null;
  yearsOfExperience: number | null;
  skills: string[];
}
