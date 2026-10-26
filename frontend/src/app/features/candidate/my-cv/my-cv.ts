import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin, interval, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CandidateService } from '../../../core/services/candidate.service';
import {
  CandidateCv,
  CvAnalysis,
  CvCoaching,
  EXPERIENCE_LEVEL_LABELS,
  ExperienceLevel,
} from '../../../core/models/candidate.model';
import { getApiErrorMessage } from '../../../core/utils/http-error.util';

/** Étapes du pipeline affichées pendant l'analyse (environ 20 s côté serveur). */
const PIPELINE_STEPS = [
  { icon: 'description', label: 'Lecture du PDF' },
  { icon: 'shield', label: 'Anonymisation des données personnelles' },
  { icon: 'psychology', label: 'Analyse par Gemini' },
  { icon: 'rule', label: 'Normalisation des compétences' },
] as const;

const MAX_SIZE_BYTES = 5 * 1024 * 1024;

interface EditableSkill {
  name: string;
  original: string | null;
  fromAi: boolean;
}

/**
 * Module 1 - « Mon CV » : dépôt du PDF, analyse IA, vérification et validation par le candidat.
 * L'IA propose, le candidat décide : rien n'est enregistré dans le profil avant « Valider ».
 */
@Component({
  selector: 'app-my-cv',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './my-cv.html',
  styleUrl: './my-cv.scss',
})
export class MyCv implements OnInit {
  private readonly candidateService = inject(CandidateService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly steps = PIPELINE_STEPS;
  protected readonly levelLabels = EXPERIENCE_LEVEL_LABELS;

  protected readonly loading = signal(true);
  protected readonly cv = signal<CandidateCv | null>(null);
  protected readonly analysis = signal<CvAnalysis | null>(null);

  protected readonly dragOver = signal(false);
  protected readonly uploading = signal(false);
  protected readonly uploadError = signal<string | null>(null);

  protected readonly analyzing = signal(false);
  protected readonly currentStep = signal(0);
  protected readonly analysisError = signal<string | null>(null);

  protected readonly applying = signal(false);

  protected readonly coaching = signal<CvCoaching | null>(null);
  protected readonly coachingRunning = signal(false);
  protected readonly coachingError = signal<string | null>(null);
  protected readonly applied = signal(false);

  // Proposition modifiable par le candidat avant validation.
  protected headline = '';
  protected summary = '';
  protected readonly years = signal<number | null>(null);
  protected readonly skills = signal<EditableSkill[]>([]);
  protected newSkill = '';

  /** Niveau recalculé avec la même règle que le serveur (0-2 / 3-5 / 6+). */
  protected readonly level = computed<ExperienceLevel | null>(() => {
    const years = this.years();
    if (years === null || years < 0) {
      return null;
    }
    return years <= 2 ? 'JUNIOR' : years <= 5 ? 'INTERMEDIATE' : 'SENIOR';
  });

  protected readonly proposal = computed(() => {
    const analysis = this.analysis();
    return analysis?.status === 'COMPLETED' ? analysis.proposal : null;
  });

  ngOnInit(): void {
    const notFound = (error: HttpErrorResponse) => (error.status === 404 ? of(null) : of(undefined));
    forkJoin({
      cv: this.candidateService.getMyCv().pipe(catchError(notFound)),
      analysis: this.candidateService.getMyLatestAnalysis().pipe(catchError(notFound)),
      coaching: this.candidateService.getMyLatestCoaching().pipe(catchError(notFound)),
    }).subscribe(({ cv, analysis, coaching }) => {
      this.cv.set(cv ?? null);
      this.setAnalysis(analysis ?? null);
      this.coaching.set(coaching ?? null);
      this.loading.set(false);
    });
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files.item(0);
    if (file) {
      this.upload(file);
    }
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    if (file) {
      this.upload(file);
    }
    input.value = '';
  }

  private upload(file: File): void {
    this.uploadError.set(null);
    // Contrôle immédiat pour l'utilisateur ; le serveur revérifie (taille, signature %PDF-).
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      this.uploadError.set('Le CV doit être un fichier PDF.');
      return;
    }
    if (file.size > MAX_SIZE_BYTES) {
      this.uploadError.set('Le CV ne doit pas dépasser 5 Mo.');
      return;
    }

    this.uploading.set(true);
    this.candidateService.uploadMyCv(file).subscribe({
      next: (cv) => {
        this.cv.set(cv);
        this.uploading.set(false);
        this.snackBar.open('CV enregistré. Vous pouvez lancer l’analyse.', 'OK', { duration: 4000 });
      },
      error: (error) => {
        this.uploadError.set(getApiErrorMessage(error, 'Le CV n’a pas pu être enregistré.'));
        this.uploading.set(false);
      },
    });
  }

  protected openCv(): void {
    const cv = this.cv();
    if (!cv) {
      return;
    }
    this.candidateService.downloadCv(cv.candidateId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (error) => this.snackBar.open(getApiErrorMessage(error, 'CV indisponible.'), 'OK', { duration: 5000 }),
    });
  }

  protected analyze(): void {
    this.analysisError.set(null);
    this.applied.set(false);
    this.analyzing.set(true);
    this.currentStep.set(0);

    // Progression indicative : le serveur ne renvoie qu'une réponse finale.
    const ticker = interval(4500)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.currentStep.update((step) => Math.min(step + 1, PIPELINE_STEPS.length - 1)));

    this.candidateService.analyzeMyCv().subscribe({
      next: (analysis) => {
        ticker.unsubscribe();
        this.setAnalysis(analysis);
        this.analyzing.set(false);
      },
      error: (error) => {
        ticker.unsubscribe();
        this.analysisError.set(getApiErrorMessage(error, 'L’analyse a échoué. Veuillez réessayer.'));
        this.analyzing.set(false);
      },
    });
  }

  protected removeSkill(skill: EditableSkill): void {
    this.skills.update((skills) => skills.filter((s) => s !== skill));
  }

  protected addSkill(): void {
    const name = this.newSkill.trim().replace(/\s+/g, ' ');
    if (!name) {
      return;
    }
    const exists = this.skills().some((s) => s.name.toLowerCase() === name.toLowerCase());
    if (!exists) {
      this.skills.update((skills) => [...skills, { name, original: null, fromAi: false }]);
    }
    this.newSkill = '';
  }

  protected setYears(value: string | number | null): void {
    const years = value === null || value === '' ? null : Number(value);
    this.years.set(years === null || Number.isNaN(years) ? null : Math.max(0, Math.min(60, Math.trunc(years))));
  }

  protected apply(): void {
    const analysis = this.analysis();
    if (!analysis) {
      return;
    }
    this.applying.set(true);
    this.candidateService.applyAnalysis({
      analysisId: analysis.id,
      headline: this.headline.trim() || null,
      summary: this.summary.trim() || null,
      yearsOfExperience: this.years(),
      skills: this.skills().map((skill) => skill.name),
    }).subscribe({
      next: () => {
        this.applying.set(false);
        this.applied.set(true);
        this.snackBar.open('Profil mis à jour avec vos informations validées.', 'OK', { duration: 4000 });
      },
      error: (error) => {
        this.applying.set(false);
        this.snackBar.open(getApiErrorMessage(error, 'La validation a échoué.'), 'OK', { duration: 6000 });
      },
    });
  }

  protected coach(): void {
    this.coachingError.set(null);
    this.coachingRunning.set(true);
    this.candidateService.coachMyCv().subscribe({
      next: (coaching) => {
        this.coaching.set(coaching);
        this.coachingRunning.set(false);
      },
      error: (error) => {
        this.coachingError.set(getApiErrorMessage(error, 'Le Coach CV est indisponible. Veuillez réessayer.'));
        this.coachingRunning.set(false);
      },
    });
  }

  /** Couleur de la jauge : rouge < 50, orange < 75, vert sinon. */
  protected scoreTone(score: number): 'low' | 'medium' | 'high' {
    return score < 50 ? 'low' : score < 75 ? 'medium' : 'high';
  }

  protected formatSize(bytes: number): number {
    return bytes / 1024;
  }

  private setAnalysis(analysis: CvAnalysis | null): void {
    this.analysis.set(analysis);
    this.applied.set(!!analysis?.appliedAt);
    const proposal = analysis?.status === 'COMPLETED' ? analysis.proposal : null;
    this.headline = proposal?.headline ?? '';
    this.summary = proposal?.summary ?? '';
    this.years.set(proposal?.yearsOfExperience ?? null);
    this.skills.set((proposal?.skills ?? []).map((skill) => ({
      name: skill.name,
      original: skill.original !== skill.name ? skill.original : null,
      fromAi: true,
    })));
  }
}
