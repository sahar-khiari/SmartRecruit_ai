import { OffreEmploi } from './job-offer.model';

/** Module 2 : statuts du cycle de vie d'une offre. */
export type StatutOffre = 'BROUILLON' | 'PUBLIEE' | 'CLOTUREE' | 'ARCHIVEE';

export type ModeTravail = 'PRESENTIEL' | 'HYBRIDE' | 'TELETRAVAIL';

export const STATUT_LABELS: Record<StatutOffre, string> = {
  BROUILLON: 'Brouillon',
  PUBLIEE: 'Publiée',
  CLOTUREE: 'Clôturée',
  ARCHIVEE: 'Archivée',
};

export const MODE_TRAVAIL_LABELS: Record<ModeTravail, string> = {
  PRESENTIEL: 'Présentiel',
  HYBRIDE: 'Hybride',
  TELETRAVAIL: 'Télétravail',
};

/** Offre complète renvoyée par le backend (champs historiques + champs du Module 2). */
export interface OffreDetaillee extends OffreEmploi {
  statut: StatutOffre;
  entreprise: string | null;
  modeTravail: ModeTravail | null;
  experienceMinAnnees: number | null;
  salaireMin: number | null;
  salaireMax: number | null;
  dateLimite: string | null;
  motsCles: string | null;
  datePublication: string | null;
}

export interface OffreFormPayload {
  titrePoste: string;
  description: string;
  niveauRequis: string;
  competencesRequises: string;
  localisation: string;
  typeContrat: string;
  dureeTestMinutes: number;
  entreprise: string | null;
  modeTravail: ModeTravail | null;
  experienceMinAnnees: number | null;
  salaireMin: number | null;
  salaireMax: number | null;
  dateLimite: string | null;
  motsCles: string | null;
  publier?: boolean;
}

export interface OffreRechercheFiltres {
  q?: string;
  localisation?: string;
  typeContrat?: string;
  niveauRequis?: string;
  modeTravail?: string;
  competence?: string;
  salaireMin?: number | null;
  statut?: StatutOffre | '';
}

// --------------------------------------------------------------------- IA (Cohere)

export interface IaDescriptionRequest {
  titrePoste: string;
  niveauRequis?: string;
  typeContrat?: string;
  localisation?: string;
  modeTravail?: string;
  entreprise?: string;
  competences?: string;
  notes?: string;
}

export interface IaDescriptionResponse {
  description: string;
  accroche: string;
  missions: string[];
  profil: string[];
  avantages: string[];
  modele: string;
  versionPrompt: string;
  depuisCache: boolean;
}

export interface CompetenceSuggeree {
  nom: string;
  categorie: string;
  score: number;
  source: string;
}

export interface IaCompetencesResponse {
  competences: CompetenceSuggeree[];
  methode: string;
  modele: string;
  depuisCache: boolean;
}

export interface IaMotsClesResponse {
  motsCles: { mot: string; importance: number }[];
  motsClesBaseline: string[];
  modele: string;
  depuisCache: boolean;
}
