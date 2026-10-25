import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatchingService } from '../../../core/services/matching.service';
import { MatchingRecommandation } from '../../../core/models/matching.model';

/**
 * Page "Offres recommandées" pour le candidat.
 * Affiche les recommandations triées par rang avec le score de matching,
 * le verdict coloré, et les boutons accept/reject.
 */
@Component({
  selector: 'app-matching-recommendations',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    MatButtonModule, MatCardModule, MatChipsModule,
    MatIconModule, MatProgressSpinnerModule,
    MatSnackBarModule, MatTooltipModule,
  ],
  template: `
    <div class="rec-page">
      <div class="rec-page__header">
        <h1><mat-icon>recommend</mat-icon> Offres recommandées pour moi</h1>
        <p>Ces offres ont été sélectionnées en fonction de votre profil CV et de vos compétences.</p>
      </div>

      @if (loading()) {
        <div class="rec-page__spinner">
          <mat-spinner diameter="48" />
          <p>Chargement de vos recommandations…</p>
        </div>
      }

      @if (!loading() && recommendations().length === 0) {
        <div class="rec-page__empty">
          <mat-icon>search_off</mat-icon>
          <h3>Aucune recommandation disponible</h3>
          <p>Complétez votre profil CV pour recevoir des recommandations personnalisées.</p>
          <a mat-flat-button color="primary" routerLink="/app/matching/profile">
            <mat-icon>edit</mat-icon> Compléter mon profil
          </a>
        </div>
      }

      @if (!loading() && recommendations().length > 0) {
        <div class="rec-page__list">
          @for (rec of recommendations(); track rec.id) {
            <mat-card class="rec-card" [class.rec-card--accepted]="rec.statut === 'ACCEPTEE'"
                      [class.rec-card--rejected]="rec.statut === 'REJETEE'">
              <mat-card-header>
                <div mat-card-avatar class="rec-card__rank">{{ rec.rang }}</div>
                <mat-card-title>{{ rec.itemLabel || 'Offre #' + rec.itemId }}</mat-card-title>
                <mat-card-subtitle>{{ rec.raison }}</mat-card-subtitle>
                <div class="rec-card__badges">
                  <span class="badge" [class]="'badge--' + scoreClass(rec.score)">
                    {{ (rec.score * 100).toFixed(0) }}%
                  </span>
                  <span class="badge badge--status">{{ statutLabel(rec.statut) }}</span>
                </div>
              </mat-card-header>

              <mat-card-content>
                <!-- Barre de score -->
                <div class="rec-card__score-bar">
                  <div class="rec-card__score-fill"
                       [style.width.%]="rec.score * 100"
                       [class]="'rec-card__score-fill--' + scoreClass(rec.score)">
                  </div>
                </div>
              </mat-card-content>

              @if (rec.statut === 'EN_ATTENTE') {
                <mat-card-actions align="end">
                  <button mat-button color="warn" (click)="reject(rec)">
                    <mat-icon>thumb_down</mat-icon> Pas intéressé
                  </button>
                  <button mat-flat-button color="primary" (click)="accept(rec)">
                    <mat-icon>thumb_up</mat-icon> Intéressé
                  </button>
                </mat-card-actions>
              }
            </mat-card>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .rec-page {
      max-width: 900px;
      margin: 0 auto;
      padding: 24px;
    }
    .rec-page__header {
      margin-bottom: 24px;
      h1 { display: flex; align-items: center; gap: 8px; font-size: 1.5rem; margin: 0 0 8px; }
      p  { color: #666; margin: 0; }
    }
    .rec-page__spinner, .rec-page__empty {
      display: flex; flex-direction: column; align-items: center;
      padding: 48px; gap: 16px; color: #888;
      mat-icon { font-size: 48px; width: 48px; height: 48px; }
    }
    .rec-page__list { display: flex; flex-direction: column; gap: 16px; }
    .rec-card { position: relative; }
    .rec-card--accepted { border-left: 4px solid #4caf50; }
    .rec-card--rejected { opacity: 0.6; }
    .rec-card__rank {
      background: #3f51b5; color: white; border-radius: 50%;
      width: 36px; height: 36px;
      display: flex; align-items: center; justify-content: center;
      font-weight: bold; font-size: 1rem;
    }
    .rec-card__badges { display: flex; gap: 8px; margin-left: auto; align-self: center; }
    .badge {
      padding: 4px 12px; border-radius: 12px; font-size: 0.75rem; font-weight: 600;
    }
    .badge--high   { background: #e8f5e9; color: #2e7d32; }
    .badge--medium { background: #fff8e1; color: #f57f17; }
    .badge--low    { background: #ffebee; color: #c62828; }
    .badge--status { background: #e3f2fd; color: #1565c0; }
    .rec-card__score-bar {
      height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; margin-top: 8px;
    }
    .rec-card__score-fill {
      height: 100%; border-radius: 4px; transition: width 0.5s ease;
      &--high   { background: #4caf50; }
      &--medium { background: #ff9800; }
      &--low    { background: #f44336; }
    }
  `],
})
export class MatchingRecommendations implements OnInit {
  private readonly matchingService = inject(MatchingService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(true);
  readonly recommendations = signal<MatchingRecommandation[]>([]);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.matchingService.getMesRecommandations().subscribe({
      next: (recs) => {
        this.recommendations.set(recs);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  accept(rec: MatchingRecommandation): void {
    this.matchingService.sendFeedback({
      recommandationId: rec.id,
      action: 'ACCEPTEE',
    }).subscribe({
      next: (updated) => {
        this.recommendations.update(recs =>
          recs.map(r => r.id === updated.id ? updated : r));
        this.snackBar.open('✅ Intérêt enregistré !', 'Fermer', { duration: 3000 });
      },
    });
  }

  reject(rec: MatchingRecommandation): void {
    this.matchingService.sendFeedback({
      recommandationId: rec.id,
      action: 'REJETEE',
    }).subscribe({
      next: (updated) => {
        this.recommendations.update(recs =>
          recs.map(r => r.id === updated.id ? updated : r));
        this.snackBar.open('Recommandation masquée.', 'Fermer', { duration: 2000 });
      },
    });
  }

  scoreClass(score: number): string {
    if (score >= 0.7) return 'high';
    if (score >= 0.4) return 'medium';
    return 'low';
  }

  statutLabel(s: string): string {
    switch (s) {
      case 'ACCEPTEE': return '✅ Intéressé';
      case 'REJETEE':  return '⛔ Ignoré';
      default:         return '⏳ En attente';
    }
  }
}
