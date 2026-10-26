import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CANDIDATE_ENDPOINTS } from '../constants/candidate-endpoints.constants';
import { SUPPRESS_ERROR_NOTIFICATION } from '../interceptors/error-notification.context';
import { MessageResponse } from '../models/auth.model';
import { PageResponse } from '../models/page.model';
import {
  CandidateCv,
  CandidateProfile,
  CandidateProfileUpdateRequest,
  CandidateSummary,
  CvAnalysis,
  CvAnalysisApplyRequest,
  CvCoaching,
  ExperienceLevel,
} from '../models/candidate.model';

/**
 * Module 1 - appels Spring de la gestion des candidats.
 * Angular n'appelle jamais le service IA : l'analyse passe par Spring, qui détient la clé interne.
 * Les erreurs sont affichées dans la page (SUPPRESS_ERROR_NOTIFICATION), pas en notification globale.
 */
@Injectable({ providedIn: 'root' })
export class CandidateService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;
  private readonly silent = { context: new HttpContext().set(SUPPRESS_ERROR_NOTIFICATION, true) };

  getMyProfile(): Observable<CandidateProfile> {
    return this.http.get<CandidateProfile>(`${this.api}${CANDIDATE_ENDPOINTS.me}`, this.silent);
  }

  updateMyProfile(request: CandidateProfileUpdateRequest): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${this.api}${CANDIDATE_ENDPOINTS.me}`, request, this.silent);
  }

  updateMySkills(skills: string[]): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${this.api}${CANDIDATE_ENDPOINTS.mySkills}`, { skills }, this.silent);
  }

  getMyCv(): Observable<CandidateCv> {
    return this.http.get<CandidateCv>(`${this.api}${CANDIDATE_ENDPOINTS.myCv}`, this.silent);
  }

  uploadMyCv(file: File): Observable<CandidateCv> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<CandidateCv>(`${this.api}${CANDIDATE_ENDPOINTS.myCv}`, body, this.silent);
  }

  /** Environ 20 secondes : lecture du PDF, anonymisation, Gemini, normalisation. */
  analyzeMyCv(): Observable<CvAnalysis> {
    return this.http.post<CvAnalysis>(`${this.api}${CANDIDATE_ENDPOINTS.analyze}`, null, this.silent);
  }

  getMyLatestAnalysis(): Observable<CvAnalysis> {
    return this.http.get<CvAnalysis>(`${this.api}${CANDIDATE_ENDPOINTS.analysis}`, this.silent);
  }

  applyAnalysis(request: CvAnalysisApplyRequest): Observable<CandidateProfile> {
    return this.http.post<CandidateProfile>(`${this.api}${CANDIDATE_ENDPOINTS.applyAnalysis}`, request, this.silent);
  }

  /** Coach CV : score /100 et conseils d'amélioration (environ 15 s). */
  coachMyCv(): Observable<CvCoaching> {
    return this.http.post<CvCoaching>(`${this.api}${CANDIDATE_ENDPOINTS.coach}`, null, this.silent);
  }

  getMyLatestCoaching(): Observable<CvCoaching> {
    return this.http.get<CvCoaching>(`${this.api}${CANDIDATE_ENDPOINTS.coach}`, this.silent);
  }

  searchCandidates(search: string, level: ExperienceLevel | null, page: number, size: number):
      Observable<PageResponse<CandidateSummary>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'lastName,asc');
    if (search.trim()) {
      params = params.set('search', search.trim());
    }
    if (level) {
      params = params.set('level', level);
    }
    return this.http.get<PageResponse<CandidateSummary>>(`${this.api}${CANDIDATE_ENDPOINTS.list}`,
      { params, ...this.silent });
  }

  getCandidate(id: number): Observable<CandidateProfile> {
    return this.http.get<CandidateProfile>(`${this.api}${CANDIDATE_ENDPOINTS.byId(id)}`, this.silent);
  }

  /** PDF du CV courant (RECRUITER, ADMIN ou le candidat lui-même), à ouvrir dans un nouvel onglet. */
  downloadCv(id: number): Observable<Blob> {
    return this.http.get(`${this.api}${CANDIDATE_ENDPOINTS.cvFile(id)}`, { responseType: 'blob', ...this.silent });
  }

  deleteCandidate(id: number): Observable<MessageResponse> {
    return this.http.delete<MessageResponse>(`${this.api}${CANDIDATE_ENDPOINTS.admin.byId(id)}`, this.silent);
  }
}
