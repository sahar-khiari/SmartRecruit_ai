import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatRadioModule } from '@angular/material/radio';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTabsModule } from '@angular/material/tabs';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { TechAssessmentService } from '../../core/services/tech-assessment.service';
import {
  CompetenceManquante,
  EvaluationResult,
  GenerateTestRequest,
  NiveauCompetence,
  RoadmapResult,
  TechTestPublic,
} from '../../core/models/tech-assessment.model';

@Component({
  selector: 'app-tech-assessment',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatRadioModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatTabsModule,
    MatExpansionModule,
    MatDividerModule,
  ],
  templateUrl: './tech-assessment.html',
  styleUrl: './tech-assessment.scss',
})
export class TechAssessment {
  private readonly assessmentService = inject(TechAssessmentService);

  // Étape actuelle : 'config' | 'test' | 'result' | 'roadmap'
  protected readonly currentStep = signal<'config' | 'test' | 'result' | 'roadmap'>('config');

  // États de chargement et d'erreur
  protected readonly loading = signal(false);
  protected readonly loadingMessage = signal('');
  protected readonly errorMessage = signal<string | null>(null);

  // Formulaire de configuration
  protected jobTitle = 'Développeur Backend Python';
  protected jobDescription = 'Conception d’APIs REST performantes et conteneurisation Docker des microservices.';
  protected candidateId = 'cand-aziz-001';
  protected promptVersion: 'v1' | 'v2' | 'v3' = 'v2';

  protected skills: CompetenceManquante[] = [
    { skill: 'Docker', level: 'débutant' },
    { skill: 'FastAPI', level: 'intermédiaire' },
  ];

  protected newSkillName = '';
  protected newSkillLevel: NiveauCompetence = 'débutant';

  // Données du test en cours
  protected readonly currentTest = signal<TechTestPublic | null>(null);

  // Réponses du candidat
  protected answers: Record<string, string> = {};
  protected exerciseCode = '';

  // Résultats d'évaluation
  protected readonly evaluationResult = signal<EvaluationResult | null>(null);

  // Feuille de route
  protected readonly roadmapResult = signal<RoadmapResult | null>(null);

  // Helpers UI
  protected addSkill(): void {
    if (this.newSkillName.trim()) {
      this.skills.push({
        skill: this.newSkillName.trim(),
        level: this.newSkillLevel,
      });
      this.newSkillName = '';
      this.newSkillLevel = 'débutant';
    }
  }

  protected removeSkill(index: number): void {
    this.skills.splice(index, 1);
  }

  protected loadPreset(preset: 'python' | 'frontend' | 'devops'): void {
    if (preset === 'python') {
      this.jobTitle = 'Développeur Backend Python';
      this.jobDescription = 'Conception d’APIs REST performantes et conteneurisation Docker des microservices.';
      this.skills = [
        { skill: 'Docker', level: 'débutant' },
        { skill: 'FastAPI', level: 'intermédiaire' },
      ];
    } else if (preset === 'frontend') {
      this.jobTitle = 'Développeur Frontend React / TypeScript';
      this.jobDescription = 'Conception d’interfaces réactives, gestion d’état globale et typage strict.';
      this.skills = [
        { skill: 'TypeScript', level: 'débutant' },
        { skill: 'React Hooks & State', level: 'intermédiaire' },
      ];
    } else if (preset === 'devops') {
      this.jobTitle = 'Ingénieur DevOps Cloud';
      this.jobDescription = 'Automatisation CI/CD, orchestration de conteneurs et infrastructure as code.';
      this.skills = [
        { skill: 'Kubernetes', level: 'débutant' },
        { skill: 'GitHub Actions', level: 'intermédiaire' },
      ];
    }
  }

  // Action 1 : Générer le test via l'IA
  protected generateTest(): void {
    if (!this.skills.length) {
      this.errorMessage.set('Veuillez ajouter au moins une compétence manquante.');
      return;
    }

    this.loading.set(true);
    this.loadingMessage.set('L’IA analyse le poste et rédige 5 questions ciblées + 1 exercice pratique...');
    this.errorMessage.set(null);

    const request: GenerateTestRequest = {
      job_title: this.jobTitle,
      job_description: this.jobDescription,
      competences_manquantes: this.skills,
      candidate_id: this.candidateId,
    };

    this.assessmentService.generateTest(request, this.promptVersion).subscribe({
      next: (test) => {
        this.currentTest.set(test);
        this.answers = {};
        this.exerciseCode = `# Écrivez votre solution pour : ${test.exercice.titre}\n\n`;
        this.currentStep.set('test');
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const detail = err.error?.detail || 'Impossible de joindre le service IA sur le port 8001. Vérifiez que uvicorn est lancé.';
        this.errorMessage.set(detail);
      },
    });
  }

  // Action 2 : Soumettre les réponses pour correction IA
  protected submitTest(): void {
    const test = this.currentTest();
    if (!test) return;

    this.loading.set(true);
    this.loadingMessage.set('L’IA évalue chaque réponse et analyse le code soumis...');
    this.errorMessage.set(null);

    this.assessmentService
      .submitTest(test.test_id, {
        candidate_id: this.candidateId,
        reponses_questions: this.answers,
        code_exercice: this.exerciseCode,
      })
      .subscribe({
        next: (response) => {
          this.evaluationResult.set(response.evaluation);
          this.currentStep.set('result');
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          const detail = err.error?.detail || 'Erreur lors de la correction par l’IA.';
          this.errorMessage.set(detail);
        },
      });
  }

  // Action 3 : Générer la Roadmap d'apprentissage
  protected generateRoadmap(): void {
    this.loading.set(true);
    this.loadingMessage.set('L’IA élabore une feuille de route sur mesure basée sur vos lacunes...');
    this.errorMessage.set(null);

    this.assessmentService
      .generateRoadmap({
        candidate_id: this.candidateId,
        competences_manquantes: this.skills,
        evaluation: this.evaluationResult() || undefined,
        duree_plan_semaines: 8,
      })
      .subscribe({
        next: (roadmap) => {
          this.roadmapResult.set(roadmap);
          this.currentStep.set('roadmap');
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          const detail = err.error?.detail || 'Erreur lors de la génération de la feuille de route.';
          this.errorMessage.set(detail);
        },
      });
  }

  // Réinitialiser
  protected reset(): void {
    this.currentStep.set('config');
    this.currentTest.set(null);
    this.evaluationResult.set(null);
    this.roadmapResult.set(null);
    this.errorMessage.set(null);
  }
}
