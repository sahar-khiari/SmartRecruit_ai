import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SUPPRESS_ERROR_NOTIFICATION } from '../interceptors/error-notification.context';

/** Offre minimale pour la sélection (compatible OffreEmploi et OffreDetaillee). */
export interface OffreSelectionnable {
  id: number;
  titrePoste: string;
}

export interface OffreComparee {
  offreId: number;
  titrePoste: string;
  missions: string;
  niveau: string;
  competencesCles: string[];
  contrat: string;
  lieu: string;
  pointsForts: string[];
  profilIdeal: string;
}

export interface OffreComparaison {
  offres: OffreComparee[];
  recommandation: { offreId: number | null; justification: string };
  profilUtilise: boolean;
  modele: string;
}

export const MAX_OFFRES_COMPAREES = 3;

/**
 * Module 2 - comparaison IA d'offres (Cohere via Spring).
 * Garde la sélection du candidat (2 à 3 offres) entre les pages de la liste.
 */
@Injectable({ providedIn: 'root' })
export class OffreComparaisonService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/offres/comparer`;

  private readonly selection = signal<OffreSelectionnable[]>([]);
  readonly selectionnees = this.selection.asReadonly();
  readonly nombre = computed(() => this.selection().length);
  readonly pleine = computed(() => this.selection().length >= MAX_OFFRES_COMPAREES);

  estSelectionnee(id: number): boolean {
    return this.selection().some((offre) => offre.id === id);
  }

  basculer(offre: OffreSelectionnable): void {
    if (this.estSelectionnee(offre.id)) {
      this.selection.update((liste) => liste.filter((o) => o.id !== offre.id));
    } else if (!this.pleine()) {
      this.selection.update((liste) => [...liste, { id: offre.id, titrePoste: offre.titrePoste }]);
    }
  }

  vider(): void {
    this.selection.set([]);
  }

  /** Cohere peut prendre jusqu'à environ une minute. */
  comparer(profil: string): Observable<OffreComparaison> {
    return this.http.post<OffreComparaison>(
      this.url,
      { offreIds: this.selection().map((o) => o.id), profil: profil.trim() || null },
      { context: new HttpContext().set(SUPPRESS_ERROR_NOTIFICATION, true) },
    );
  }
}
