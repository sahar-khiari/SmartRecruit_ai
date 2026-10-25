import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CandidatProfile,
  CandidatProfileRequest,
  FeedbackRequest,
  MatchingCritere,
  MatchingCritereRequest,
  MatchingRecommandation,
  MatchingScore,
  MatchingStatus,
} from '../models/matching.model';

/**
 * Service Angular du module matching.
 * Appelle uniquement l'API Spring Boot (/api/matching/...).
 * Jamais d'appel direct à FastAPI.
 */
@Injectable({ providedIn: 'root' })
export class MatchingService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/matching`;

  // ── Statut ────────────────────────────────────────────────────────────────
  getStatus(): Observable<MatchingStatus> {
    return this.http.get<MatchingStatus>(`${this.base}/status`);
  }

  // ── Profil candidat ───────────────────────────────────────────────────────
  getMonProfil(): Observable<CandidatProfile> {
    return this.http.get<CandidatProfile>(`${this.base}/profile`);
  }

  saveMonProfil(req: CandidatProfileRequest): Observable<CandidatProfile> {
    return this.http.put<CandidatProfile>(`${this.base}/profile`, req);
  }

  uploadPdf(file: File, niveau?: string): Observable<CandidatProfile> {
    const form = new FormData();
    form.append('file', file);
    if (niveau) form.append('niveau', niveau);
    return this.http.post<CandidatProfile>(`${this.base}/profile/upload`, form);
  }

  getCandidateProfile(userId: number): Observable<CandidatProfile> {
    return this.http.get<CandidatProfile>(`${this.base}/profile/${userId}`);
  }

  // ── Critères ──────────────────────────────────────────────────────────────
  getCriteres(offreId: number): Observable<MatchingCritere[]> {
    return this.http.get<MatchingCritere[]>(`${this.base}/criteria`, {
      params: { offreId },
    });
  }

  suggestCriteres(offreId: number): Observable<MatchingCritere[]> {
    return this.http.get<MatchingCritere[]>(`${this.base}/criteria/suggest`, {
      params: { offreId },
    });
  }

  createCritere(req: MatchingCritereRequest): Observable<MatchingCritere> {
    return this.http.post<MatchingCritere>(`${this.base}/criteria`, req);
  }

  updateCritere(id: number, req: MatchingCritereRequest): Observable<MatchingCritere> {
    return this.http.put<MatchingCritere>(`${this.base}/criteria/${id}`, req);
  }

  deleteCritere(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/criteria/${id}`);
  }

  // ── Matching ──────────────────────────────────────────────────────────────
  runMatching(offreId: number): Observable<MatchingScore[]> {
    return this.http.post<MatchingScore[]>(`${this.base}/offers/${offreId}/run`, {});
  }

  getRanking(offreId: number): Observable<MatchingScore[]> {
    return this.http.get<MatchingScore[]>(`${this.base}/offers/${offreId}/ranking`);
  }

  explain(candidateId: number, offreId: number): Observable<MatchingScore> {
    return this.http.get<MatchingScore>(`${this.base}/${candidateId}/${offreId}/explain`);
  }

  // ── Recommandations ───────────────────────────────────────────────────────
  getMesRecommandations(): Observable<MatchingRecommandation[]> {
    return this.http.get<MatchingRecommandation[]>(`${this.base}/candidates/me/recommendations`);
  }

  // ── Feedback ──────────────────────────────────────────────────────────────
  sendFeedback(req: FeedbackRequest): Observable<MatchingRecommandation> {
    return this.http.post<MatchingRecommandation>(`${this.base}/feedback`, req);
  }
}
