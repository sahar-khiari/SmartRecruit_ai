import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatchingService } from '../../../core/services/matching.service';
import { MatchingScore, EngineMode } from '../../../core/models/matching.model';

/**
 * Page de classement des candidats pour une offre (vue Recruteur).
 * Affiche les scores, les barres de progression par composante,
 * les badges de verdict et de moteur, et les compétences matchées/manquantes.
 */
@Component({
  selector: 'app-matching-ranking',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule, MatCardModule, MatChipsModule,
    MatExpansionModule, MatIconModule,
    MatProgressBarModule, MatProgressSpinnerModule,
    MatSnackBarModule, MatTooltipModule,
  ],
  template: `
    <div class="ranking-page">
      <div class="ranking-page__header">
        <h1><mat-icon>leaderboard</mat-icon> Classement des candidats</h1>
        <div class="ranking-page__actions">
          <button mat-flat-button color="primary" (click)="runMatching()" [disabled]="running()">
            @if (running()) { <mat-spinner diameter="20" /> }
            @else { <mat-icon>play_arrow</mat-icon> }
            Lancer le matching
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="ranking-page__spinner">
          <mat-spinner diameter="48" />
          <p>Calcul du matching en cours…</p>
        </div>
      }

      @if (!loading() && scores().length === 0) {
        <div class="ranking-page__empty">
          <mat-icon>group_off</mat-icon>
          <h3>Aucun résultat de matching</h3>
          <p>Cliquez sur « Lancer le matching » pour analyser les candidatures.</p>
        </div>
      }

      @if (!loading() && scores().length > 0) {
        <div class="ranking-page__list">
          @for (score of scores(); track score.id; let i = $index) {
            <mat-expansion-panel class="ranking-card"
                                  [class.ranking-card--strong]="score.verdict === 'STRONG_MATCH'"
                                  [class.ranking-card--possible]="score.verdict === 'POSSIBLE'"
                                  [class.ranking-card--weak]="score.verdict === 'WEAK'">
              <mat-expansion-panel-header>
                <mat-panel-title class="ranking-card__title">
                  <span class="ranking-card__rank">{{ i + 1 }}</span>
                  <span class="ranking-card__name">{{ score.candidatNom }}</span>
                </mat-panel-title>
                <mat-panel-description class="ranking-card__desc">
                  <div class="ranking-card__badges">
                    <span class="verdict-badge verdict-badge--{{ verdictClass(score.verdict) }}">
                      {{ verdictLabel(score.verdict) }}
                    </span>
                    <span class="engine-badge engine-badge--{{ score.engineMode }}">
                      {{ score.engineMode }}
                    </span>
                    <strong>{{ (score.finalScore * 100).toFixed(0) }}%</strong>
                  </div>
                </mat-panel-description>
              </mat-expansion-panel-header>

              <!-- Détail du score -->
              <div class="ranking-detail">
                <div class="ranking-detail__bars">
                  <div class="score-row">
                    <span>Similarité sémantique</span>
                    <mat-progress-bar mode="determinate"
                                      [value]="score.semanticScore * 100"
                                      [color]="barColor(score.semanticScore)" />
                    <span>{{ (score.semanticScore * 100).toFixed(0) }}%</span>
                  </div>
                  <div class="score-row">
                    <span>Niveau d'expérience</span>
                    <mat-progress-bar mode="determinate"
                                      [value]="score.experienceScore * 100"
                                      [color]="barColor(score.experienceScore)" />
                    <span>{{ (score.experienceScore * 100).toFixed(0) }}%</span>
                  </div>
                  @if (score.testScore !== null) {
                    <div class="score-row">
                      <span>Score test technique</span>
                      <mat-progress-bar mode="determinate"
                                        [value]="(score.testScore ?? 0) * 100"
                                        color="accent" />
                      <span>{{ ((score.testScore ?? 0) * 100).toFixed(0) }}%</span>
                    </div>
                  }
                  @if (score.llmScore !== null) {
                    <div class="score-row">
                      <span>Score LLM-judge</span>
                      <mat-progress-bar mode="determinate"
                                        [value]="(score.llmScore ?? 0) * 100"
                                        color="warn" />
                      <span>{{ ((score.llmScore ?? 0) * 100).toFixed(0) }}%</span>
                    </div>
                  }
                </div>

                <!-- Compétences -->
                <div class="ranking-detail__skills">
                  @if (score.matchedSkills.length > 0) {
                    <div>
                      <strong>✅ Compétences présentes :</strong>
                      <mat-chip-set>
                        @for (s of score.matchedSkills; track s) {
                          <mat-chip color="primary">{{ s }}</mat-chip>
                        }
                      </mat-chip-set>
                    </div>
                  }
                  @if (score.missingSkills.length > 0) {
                    <div>
                      <strong>⚠️ Compétences manquantes :</strong>
                      <mat-chip-set>
                        @for (s of score.missingSkills; track s) {
                          <mat-chip color="warn">{{ s }}</mat-chip>
                        }
                      </mat-chip-set>
                    </div>
                  }
                </div>

                <!-- Explication -->
                @if (score.explication) {
                  <mat-card class="ranking-detail__explication">
                    <mat-card-content>
                      <pre class="explication-text">{{ score.explication }}</pre>
                    </mat-card-content>
                  </mat-card>
                }
              </div>
            </mat-expansion-panel>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .ranking-page {
      max-width: 1000px;
      margin: 0 auto;
      padding: 24px;
    }
    .ranking-page__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 24px;
      h1 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 1.5rem; }
    }
    .ranking-page__spinner, .ranking-page__empty {
      display: flex; flex-direction: column; align-items: center;
      padding: 48px; gap: 16px; color: #888;
      mat-icon { font-size: 48px; width: 48px; height: 48px; }
    }
    .ranking-page__list { display: flex; flex-direction: column; gap: 12px; }
    .ranking-card { border-radius: 8px !important; }
    .ranking-card--strong { border-left: 4px solid #4caf50; }
    .ranking-card--possible { border-left: 4px solid #ff9800; }
    .ranking-card--weak { border-left: 4px solid #f44336; }
    .ranking-card__title { display: flex; align-items: center; gap: 12px; }
    .ranking-card__rank {
      background: #3f51b5; color: white; border-radius: 50%;
      width: 28px; height: 28px; min-width: 28px;
      display: flex; align-items: center; justify-content: center;
      font-weight: bold; font-size: 0.85rem;
    }
    .ranking-card__desc { display: flex; justify-content: flex-end; align-items: center; }
    .ranking-card__badges { display: flex; gap: 8px; align-items: center; }
    .verdict-badge {
      padding: 2px 10px; border-radius: 10px; font-size: 0.75rem; font-weight: 600;
      &--strong  { background: #e8f5e9; color: #2e7d32; }
      &--possible { background: #fff8e1; color: #f57f17; }
      &--weak    { background: #ffebee; color: #c62828; }
    }
    .engine-badge {
      padding: 2px 8px; border-radius: 10px; font-size: 0.7rem;
      &--JAVA       { background: #e3f2fd; color: #1565c0; }
      &--EMBEDDINGS { background: #f3e5f5; color: #6a1b9a; }
      &--LLM        { background: #fff3e0; color: #bf360c; }
    }
    .ranking-detail { padding: 16px 0; }
    .ranking-detail__bars { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
    .score-row {
      display: grid;
      grid-template-columns: 180px 1fr 50px;
      align-items: center;
      gap: 12px;
      font-size: 0.875rem;
    }
    .ranking-detail__skills {
      display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px;
    }
    .ranking-detail__explication {
      background: #fafafa;
      .explication-text { white-space: pre-wrap; font-size: 0.85rem; line-height: 1.6; margin: 0; }
    }
  `],
})
export class MatchingRanking implements OnInit {
  private readonly route   = inject(ActivatedRoute);
  private readonly service = inject(MatchingService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(false);
  readonly running = signal(false);
  readonly scores  = signal<MatchingScore[]>([]);

  private offreId!: number;

  ngOnInit(): void {
    this.offreId = Number(this.route.snapshot.paramMap.get('offreId'));
    this.loadRanking();
  }

  private loadRanking(): void {
    this.loading.set(true);
    this.service.getRanking(this.offreId).subscribe({
      next: (s) => { this.scores.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  runMatching(): void {
    this.running.set(true);
    this.service.runMatching(this.offreId).subscribe({
      next: (s) => {
        this.scores.set(s);
        this.running.set(false);
        this.snackBar.open(`✅ Matching calculé pour ${s.length} candidat(s)`, 'Fermer', { duration: 3000 });
      },
      error: () => {
        this.running.set(false);
        this.snackBar.open('❌ Erreur lors du matching', 'Fermer', { duration: 4000 });
      },
    });
  }

  verdictClass(v: string): string {
    switch (v) {
      case 'STRONG_MATCH': return 'strong';
      case 'POSSIBLE':     return 'possible';
      default:             return 'weak';
    }
  }

  verdictLabel(v: string): string {
    switch (v) {
      case 'STRONG_MATCH': return '🟢 Fort potentiel';
      case 'POSSIBLE':     return '🟡 Intéressant';
      default:             return '🔴 Inadéquat';
    }
  }

  barColor(score: number): 'primary' | 'accent' | 'warn' {
    return score >= 0.6 ? 'primary' : score >= 0.4 ? 'accent' : 'warn';
  }
}
