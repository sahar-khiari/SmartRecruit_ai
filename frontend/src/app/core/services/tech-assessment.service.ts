import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  GenerateRoadmapRequest,
  GenerateTestRequest,
  RoadmapResponse,
  RoadmapResult,
  SubmitAnswersRequest,
  SubmitTestResponse,
  TechTestPublic,
} from '../models/tech-assessment.model';

@Injectable({
  providedIn: 'root',
})
export class TechAssessmentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.aiServiceUrl;

  /**
   * Génère un test technique via l'IA (FastAPI Module 4).
   */
  generateTest(
    request: GenerateTestRequest,
    promptVersion: 'v1' | 'v2' | 'v3' = 'v2',
  ): Observable<TechTestPublic> {
    const params = new HttpParams().set('prompt_version', promptVersion);
    return this.http.post<TechTestPublic>(
      `${this.baseUrl}/tests/generate`,
      request,
      { params },
    );
  }

  /**
   * Soumet les réponses d'un candidat pour correction automatique par l'IA.
   */
  submitTest(
    testId: string,
    request: SubmitAnswersRequest,
  ): Observable<SubmitTestResponse> {
    return this.http.post<SubmitTestResponse>(
      `${this.baseUrl}/tests/${testId}/submit`,
      request,
    );
  }

  /**
   * Génère une feuille de route d'apprentissage personnalisée (Roadmap).
   */
  generateRoadmap(
    request: GenerateRoadmapRequest,
  ): Observable<RoadmapResponse> {
    return this.http.post<RoadmapResponse>(
      `${this.baseUrl}/roadmap`,
      request,
    );
  }
}
