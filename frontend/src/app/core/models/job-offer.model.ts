export interface OffreEmploi {
  id: number;
  titrePoste: string;
  description: string;
  niveauRequis: string;
  competencesRequises: string;
  localisation: string;
  typeContrat: string;
  dureeTestMinutes: number;
  active: boolean;
  recruteurId: number;
  recruteurNom: string;
  createdAt: string;
  updatedAt: string;
}

export interface Candidature {
  id: number;
  offreId: number;
  offreTitre: string;
  candidatId: number;
  candidatNom: string;
  candidatEmail: string;
  scoreIa: number | null;
  recommandationIa: string | null;
  feedbackIa: string | null;
  pointsForts: string | null;
  pointsFaibles: string | null;
  testId: string | null;
  statut: string;
  createdAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface OffreEmploiCreateRequest {
  titrePoste: string;
  description: string;
  niveauRequis: string;
  competencesRequises: string;
  localisation: string;
  typeContrat: string;
  dureeTestMinutes: number;
}

export interface CandidaturePayload {
  scoreIa?: number;
  recommandationIa?: string;
  feedbackIa?: string;
  pointsForts?: string;
  pointsFaibles?: string;
  testId?: string;
}
