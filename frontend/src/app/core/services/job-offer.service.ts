import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Candidature,
  CandidaturePayload,
  OffreEmploi,
  OffreEmploiCreateRequest,
  PagedResponse,
} from '../models/job-offer.model';

@Injectable({
  providedIn: 'root',
})
export class JobOfferService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api/offres';

  /**
   * Récupère la liste paginée des offres actives.
   */
  getOffres(page = 0, size = 10): Observable<PagedResponse<OffreEmploi>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<OffreEmploi>>(this.baseUrl, { params });
  }

  /**
   * Récupère les offres du recruteur connecté.
   */
  getMesOffres(page = 0, size = 10): Observable<PagedResponse<OffreEmploi>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<OffreEmploi>>(`${this.baseUrl}/mes-offres`, { params });
  }

  /**
   * Récupère le détail d'une offre par son identifiant.
   */
  getById(id: number): Observable<OffreEmploi> {
    return this.http.get<OffreEmploi>(`${this.baseUrl}/${id}`);
  }

  /**
   * Crée une nouvelle offre d'emploi.
   */
  create(data: OffreEmploiCreateRequest): Observable<OffreEmploi> {
    return this.http.post<OffreEmploi>(this.baseUrl, data);
  }

  /**
   * Met à jour une offre existante.
   */
  update(id: number, data: OffreEmploiCreateRequest): Observable<OffreEmploi> {
    return this.http.put<OffreEmploi>(`${this.baseUrl}/${id}`, data);
  }

  /**
   * Active ou désactive une offre.
   */
  toggleStatus(id: number, active: boolean): Observable<OffreEmploi> {
    return this.http.put<OffreEmploi>(`${this.baseUrl}/${id}/status`, { active });
  }

  /**
   * Supprime une offre d'emploi.
   */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /**
   * Postule à une offre avec le résultat du test IA.
   */
  candidater(offreId: number, payload: CandidaturePayload): Observable<Candidature> {
    return this.http.post<Candidature>(`${this.baseUrl}/${offreId}/candidater`, payload);
  }

  /**
   * Récupère les candidatures reçues pour une offre (recruteur/admin).
   */
  getCandidatures(offreId: number): Observable<Candidature[]> {
    return this.http.get<Candidature[]>(`${this.baseUrl}/${offreId}/candidatures`);
  }

  /**
   * Récupère la candidature du candidat connecté pour une offre donnée (s'il a déjà postulé).
   */
  getMaCandidature(offreId: number): Observable<Candidature | null> {
    return this.http.get<Candidature | null>(`${this.baseUrl}/${offreId}/ma-candidature`);
  }

  /**
   * Récupère toutes les candidatures du candidat connecté.
   */
  getMesCandidatures(): Observable<Candidature[]> {
    return this.http.get<Candidature[]>(`${this.baseUrl}/mes-candidatures`);
  }
}
