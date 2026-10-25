export type NiveauCompetence = 'débutant' | 'intermédiaire' | 'avancé';

export interface CompetenceManquante {
  skill: string;
  level: NiveauCompetence;
}

export interface GenerateTestRequest {
  job_title: string;
  job_description: string;
  competences_manquantes: CompetenceManquante[];
  candidate_id: string;
}

export interface ChoixQCM {
  lettre: string;
  texte: string;
}

export interface Question {
  numero: number;
  type: 'qcm' | 'ouverte';
  enonce: string;
  choix?: ChoixQCM[];
  competence_ciblee: string;
  points: number;
}

export interface ExercicePratique {
  titre: string;
  consigne: string;
  competence_ciblee: string;
  points: number;
  criteres_evaluation: string[];
}

export interface TechTestPublic {
  test_id: string;
  titre: string;
  job_title: string;
  duree_minutes: number;
  questions: Question[];
  exercice: ExercicePratique;
  instructions: string;
}

export interface SubmitAnswersRequest {
  candidate_id?: string;
  reponses_questions: Record<string, string>;
  code_exercice: string;
}

export interface EvaluationResult {
  score: number;
  score_questions: number;
  score_exercice: number;
  recommandation: 'VALIDER' | 'APPROFONDIR' | 'REJETER';
  points_forts: string[];
  points_faibles: string[];
  feedback: string;
}

export interface SubmitTestResponse {
  test_id: string;
  candidate_id: string;
  evaluation: EvaluationResult;
}

export interface SemaineRoadmap {
  semaine: number;
  objectif: string;
  competences: string[];
  ressources: string[];
  mini_projet: string;
}

export interface RoadmapResult {
  titre: string;
  duree_semaines: number;
  plan: SemaineRoadmap[];
  conseil_final: string;
}

export interface GenerateRoadmapRequest {
  candidate_id: string;
  competences_manquantes: CompetenceManquante[];
  evaluation?: EvaluationResult;
  duree_plan_semaines?: number;
}
