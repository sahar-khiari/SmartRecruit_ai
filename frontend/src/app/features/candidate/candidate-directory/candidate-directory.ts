import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CandidateService } from '../../../core/services/candidate.service';
import {
  CandidateProfile,
  CandidateSummary,
  EXPERIENCE_LEVEL_LABELS,
  ExperienceLevel,
} from '../../../core/models/candidate.model';
import { getApiErrorMessage } from '../../../core/utils/http-error.util';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../../shared/components/confirmation-dialog/confirmation-dialog';

/**
 * Module 1 - annuaire des candidats (RECRUITER et ADMIN).
 * Route data `admin: true` : ajoute la suppression (réservée à l'ADMIN côté serveur).
 */
@Component({
  selector: 'app-candidate-directory',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './candidate-directory.html',
  styleUrl: './candidate-directory.scss',
})
export class CandidateDirectory implements OnInit {
  private readonly candidateService = inject(CandidateService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly isAdmin = inject(ActivatedRoute).snapshot.data['admin'] === true;
  protected readonly levelLabels = EXPERIENCE_LEVEL_LABELS;
  protected readonly levels = Object.keys(EXPERIENCE_LEVEL_LABELS) as ExperienceLevel[];
  protected readonly pageSize = 10;

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly level = new FormControl<ExperienceLevel | null>(null);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly candidates = signal<CandidateSummary[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);

  protected readonly selected = signal<CandidateProfile | null>(null);
  protected readonly detailLoading = signal(false);

  ngOnInit(): void {
    this.search.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load(0));
    this.level.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load(0));
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.candidateService.searchCandidates(this.search.value, this.level.value, page, this.pageSize).subscribe({
      next: (result) => {
        this.candidates.set(result.content);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      },
      error: (error) => {
        this.error.set(getApiErrorMessage(error, 'Impossible de charger les candidats.'));
        this.loading.set(false);
      },
    });
  }

  protected showDetail(candidate: CandidateSummary): void {
    this.detailLoading.set(true);
    this.candidateService.getCandidate(candidate.id).subscribe({
      next: (profile) => {
        this.selected.set(profile);
        this.detailLoading.set(false);
      },
      error: (error) => {
        this.detailLoading.set(false);
        this.snackBar.open(getApiErrorMessage(error, 'Candidat introuvable.'), 'OK', { duration: 5000 });
      },
    });
  }

  protected openCv(candidateId: number): void {
    this.candidateService.downloadCv(candidateId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (error) =>
        this.snackBar.open(getApiErrorMessage(error, 'Ce candidat n’a pas encore déposé de CV.'), 'OK', { duration: 5000 }),
    });
  }

  protected confirmDelete(candidate: CandidateSummary): void {
    this.dialog
      .open<ConfirmationDialog, ConfirmationDialogData, boolean>(ConfirmationDialog, {
        data: {
          title: 'Supprimer ce candidat ?',
          message: `Le compte de ${candidate.firstName} ${candidate.lastName}, son profil, ses compétences `
            + 'et ses CV seront définitivement supprimés.',
          confirmLabel: 'Supprimer',
          danger: true,
        },
        width: '500px',
        autoFocus: 'dialog',
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.delete(candidate);
        }
      });
  }

  private delete(candidate: CandidateSummary): void {
    this.candidateService.deleteCandidate(candidate.id).subscribe({
      next: () => {
        this.snackBar.open('Candidat supprimé.', 'OK', { duration: 3000 });
        if (this.selected()?.id === candidate.id) {
          this.selected.set(null);
        }
        this.load(this.candidates().length === 1 && this.page() > 0 ? this.page() - 1 : this.page());
      },
      error: (error) =>
        this.snackBar.open(getApiErrorMessage(error, 'Suppression impossible.'), 'OK', { duration: 6000 }),
    });
  }
}
