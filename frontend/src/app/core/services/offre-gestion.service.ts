import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PagedResponse } from '../models/job-offer.model';
import {
  IaCompetencesResponse,
  IaDescriptionRequest,
  IaDescriptionResponse,
  IaMotsClesResponse,
  OffreDetaillee,
  OffreFormPayload,
  OffreRechercheFiltres,
} from '../models/offre-gestion.model';

/**
 * Module 2 : recherche multicritère, cycle de vie et IA des offres (complète JobOfferService).
 * Angular appelle uniquement Spring Boot ; Spring appelle le service IA.
 */
@Injectable({ providedIn: 'root' })
export class OffreGestionService {
  private readonly http = inject(HttpClient);
  private readonly offresUrl = `${environment.apiUrl}/offres`;

  // ------------------------------------------------------------------ CRUD (champs enrichis)

  getById(id: number): Observable<OffreDetaillee> {
    return this.http.get<OffreDetaillee>(`${this.offresUrl}/${id}`);
  }

  create(payload: OffreFormPayload): Observable<OffreDetaillee> {
    return this.http.post<OffreDetaillee>(this.offresUrl, payload);
  }

  update(id: number, payload: OffreFormPayload): Observable<OffreDetaillee> {
    return this.http.put<OffreDetaillee>(`${this.offresUrl}/${id}`, payload);
  }

  // ------------------------------------------------------------- recherche

  rechercher(filtres: OffreRechercheFiltres, page = 0, size = 10): Observable<PagedResponse<OffreDetaillee>> {
    return this.http.get<PagedResponse<OffreDetaillee>>(`${this.offresUrl}/recherche`, {
      params: this.toParams(filtres, page, size),
    });
  }

  rechercherMesOffres(filtres: OffreRechercheFiltres, page = 0, size = 10): Observable<PagedResponse<OffreDetaillee>> {
    return this.http.get<PagedResponse<OffreDetaillee>>(`${this.offresUrl}/mes-offres/recherche`, {
      params: this.toParams(filtres, page, size),
    });
  }

  // -------------------------------------------------------- cycle de vie

  publier(id: number): Observable<OffreDetaillee> {
    return this.http.put<OffreDetaillee>(`${this.offresUrl}/${id}/publier`, {});
  }

  cloturer(id: number): Observable<OffreDetaillee> {
    return this.http.put<OffreDetaillee>(`${this.offresUrl}/${id}/cloturer`, {});
  }

  archiver(id: number): Observable<OffreDetaillee> {
    return this.http.put<OffreDetaillee>(`${this.offresUrl}/${id}/archiver`, {});
  }

  // ------------------------------------------------------------- IA Cohere

  iaDescription(request: IaDescriptionRequest): Observable<IaDescriptionResponse> {
    return this.http.post<IaDescriptionResponse>(`${this.offresUrl}/ia/description`, request);
  }

  iaCompetences(titrePoste: string, description: string, niveauRequis: string): Observable<IaCompetencesResponse> {
    return this.http.post<IaCompetencesResponse>(`${this.offresUrl}/ia/competences`, {
      titrePoste,
      description,
      niveauRequis,
    });
  }

  iaMotsCles(titrePoste: string, description: string): Observable<IaMotsClesResponse> {
    return this.http.post<IaMotsClesResponse>(`${this.offresUrl}/ia/mots-cles`, { titrePoste, description });
  }

  // ---------------------------------------------------------------- utils

  private toParams(filtres: OffreRechercheFiltres, page: number, size: number): HttpParams {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filtres)) {
      if (value !== null && value !== undefined && `${value}`.trim() !== '') {
        params = params.set(key, `${value}`.trim());
      }
    }
    return params;
  }
}

/** Liste « a, b, c » -> ['a', 'b', 'c'] sans doublons ni vides. */
export function splitListe(valeur: string | null | undefined): string[] {
  if (!valeur) {
    return [];
  }
  const vus = new Set<string>();
  return valeur
    .split(',')
    .map((v) => v.trim())
    .filter((v) => v && !vus.has(v.toLowerCase()) && vus.add(v.toLowerCase()));
}

/** Message lisible depuis une erreur HTTP du backend (ErrorResponse.message). */
export function messageErreur(err: unknown, defaut: string): string {
  const e = err as { error?: { message?: string }; status?: number };
  if (e?.status === 0) {
    return 'Serveur injoignable. Vérifiez que le backend est démarré.';
  }
  return e?.error?.message || defaut;
}
