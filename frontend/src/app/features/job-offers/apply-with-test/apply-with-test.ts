import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { JobOfferService } from '../../../core/services/job-offer.service';
import { TechAssessmentService } from '../../../core/services/tech-assessment.service';
import { AuthService } from '../../../core/services/auth.service';
import { OffreEmploi } from '../../../core/models/job-offer.model';
import {
  CompetenceManquante,
  EvaluationResult,
  GenerateTestRequest,
  ReponseQuestionItem,
  TechTestPublic,
  RoadmapResult,
} from '../../../core/models/tech-assessment.model';

type ApplyStep = 'detail' | 'generating' | 'test' | 'evaluating' | 'result' | 'roadmap' | 'done';

@Component({
  selector: 'app-apply-with-test',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatDividerModule,
    MatExpansionModule,
  ],
  templateUrl: './apply-with-test.html',
  styleUrl: './apply-with-test.scss',
})
export class ApplyWithTest implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly jobOfferService = inject(JobOfferService);
  private readonly assessmentService = inject(TechAssessmentService);
  private readonly authService = inject(AuthService);

  protected readonly currentStep = signal<ApplyStep>('detail');
  protected readonly loading = signal(false);
  protected readonly loadingMessage = signal('');
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly offre = signal<OffreEmploi | null>(null);
  protected readonly currentTest = signal<TechTestPublic | null>(null);
  protected readonly evaluationResult = signal<EvaluationResult | null>(null);
  protected readonly roadmapResult = signal<RoadmapResult | null>(null);
  protected readonly applicationSent = signal(false);

  protected answers: Record<string, string> = {};
  protected exerciseCode = '';

  private offreId = 0;
  private jobTitle = '';
  private skills: CompetenceManquante[] = [];

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.offreId = idParam ? parseInt(idParam, 10) : 0;
    this.loadOffre();
  }

  private loadOffre(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.jobOfferService.getById(this.offreId).subscribe({
      next: (offre) => {
        this.offre.set(offre);
        this.jobTitle = offre.titrePoste;
        this.skills = offre.competencesRequises
          .split(',')
          .map((s) => s.trim())
          .filter((s) => s.length > 0)
          .map((s) => ({ skill: s, level: 'intermédiaire' as const }));
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger les détails de l\'offre.');
        this.loading.set(false);
      },
    });
  }

  protected startTest(): void {
    const offre = this.offre();
    if (!offre) return;

    const user = this.authService.currentUser();
    const candidateId = user ? `cand-${user.id}` : 'cand-anonymous';

    this.currentStep.set('generating');
    this.loading.set(true);
    this.loadingMessage.set('L\'IA analyse l\'offre et génère un test technique personnalisé...');
    this.errorMessage.set(null);

    const desc = (offre.description && offre.description.trim().length >= 10)
      ? offre.description.trim()
      : `${offre.titrePoste} - ${offre.description || 'Description du poste'} (Niveau: ${offre.niveauRequis || 'Intermédiaire'})`;

    let skillsToSend = this.skills;
    if (!skillsToSend || skillsToSend.length === 0) {
      skillsToSend = [{ skill: 'Génie Logiciel & Développement', level: 'intermédiaire' }];
    }

    const request: GenerateTestRequest = {
      job_title: this.jobTitle || offre.titrePoste,
      job_description: desc,
      competences_manquantes: skillsToSend,
      candidate_id: candidateId,
    };

    this.assessmentService.generateTest(request, 'v2').subscribe({
      next: (test) => {
        this.currentTest.set(test);
        this.answers = {};
        this.exerciseCode = `# Solution pour : ${test.exercice.titre}\n\n`;
        this.currentStep.set('test');
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.currentStep.set('detail');
        let detail = 'Impossible de générer le test IA. Vérifiez que le service IA est disponible.';
        if (typeof err.error?.detail === 'string') {
          detail = err.error.detail;
        } else if (Array.isArray(err.error?.detail)) {
          detail = err.error.detail.map((e: any) => `${e.loc?.join('.')}: ${e.msg}`).join(' | ');
        } else if (err.error?.detail) {
          detail = JSON.stringify(err.error.detail);
        }
        this.errorMessage.set(detail);
      },
    });
  }

  protected submitTest(): void {
    const test = this.currentTest();
    if (!test) return;

    const user = this.authService.currentUser();
    const candidateId = user ? `cand-${user.id}` : 'cand-anonymous';

    const reponsesList: ReponseQuestionItem[] = (test.questions || []).map((q) => ({
      numero: q.numero,
      reponse: (this.answers[q.numero] ?? '').toString(),
    }));

    this.currentStep.set('evaluating');
    this.loading.set(true);
    this.loadingMessage.set('L\'IA évalue vos réponses...');
    this.errorMessage.set(null);

    this.assessmentService
      .submitTest(test.test_id, {
        candidate_id: candidateId,
        reponses_questions: reponsesList,
        reponse_exercice: this.exerciseCode || '',
      })
      .subscribe({
        next: (response) => {
          this.evaluationResult.set(response.evaluation);
          this.currentStep.set('result');
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.currentStep.set('test');
          const detail =
            typeof err.error?.detail === 'string'
              ? err.error.detail
              : err.error?.detail
                ? JSON.stringify(err.error.detail)
                : 'Erreur lors de la correction par l\'IA.';
          this.errorMessage.set(detail);
        },
      });
  }

  protected sendApplication(): void {
    const evaluation = this.evaluationResult();
    const test = this.currentTest();

    this.loading.set(true);
    this.errorMessage.set(null);

    const payload = {
      scoreIa: evaluation?.score,
      recommandationIa: evaluation?.recommandation,
      feedbackIa: evaluation?.feedback,
      pointsForts: JSON.stringify(evaluation?.points_forts ?? []),
      pointsFaibles: JSON.stringify(evaluation?.points_faibles ?? []),
      testId: test?.test_id,
    };

    this.jobOfferService.candidater(this.offreId, payload).subscribe({
      next: () => {
        this.applicationSent.set(true);
        this.currentStep.set('done');
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const detail =
          err.error?.message ||
          err.error?.error ||
          'Impossible d\'envoyer la candidature. Veuillez réessayer.';
        this.errorMessage.set(detail);
      },
    });
  }

  protected generateRoadmap(): void {
    const user = this.authService.currentUser();
    const candidateId = user ? `cand-${user.id}` : 'cand-anonymous';

    this.loading.set(true);
    this.loadingMessage.set('L\'IA élabore une feuille de route d\'apprentissage sur mesure basée sur votre test...');
    this.errorMessage.set(null);

    let skillsToSend = this.skills;
    if (!skillsToSend || skillsToSend.length === 0) {
      skillsToSend = [{ skill: 'Génie Logiciel & Développement', level: 'intermédiaire' }];
    }

    this.assessmentService
      .generateRoadmap({
        candidate_id: candidateId,
        competences_manquantes: skillsToSend,
        evaluation: this.evaluationResult() || undefined,
        duree_plan_semaines: 8,
      })
      .subscribe({
        next: (response: any) => {
          const roadmap: RoadmapResult = response.roadmap || response;
          this.roadmapResult.set(roadmap);
          this.currentStep.set('roadmap');
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          const detail =
            err.error?.detail ||
            'Erreur lors de la génération de la feuille de route.';
          this.errorMessage.set(detail);
        },
      });
  }

  protected backToOffers(): void {
    void this.router.navigate(['/app/offres']);
  }
}
