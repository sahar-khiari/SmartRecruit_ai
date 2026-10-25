import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatSliderModule } from '@angular/material/slider';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatchingService } from '../../../core/services/matching.service';
import { MatchingCritere } from '../../../core/models/matching.model';

/**
 * Éditeur de critères de matching pour une offre (vue Recruteur).
 * Permet d'ajouter, modifier, supprimer des critères pondérés,
 * et d'utiliser l'auto-suggestion basée sur les compétences de l'offre.
 */
@Component({
  selector: 'app-matching-criteria',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatButtonModule, MatCardModule, MatCheckboxModule,
    MatChipsModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatListModule, MatSliderModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="criteria-page">
      <div class="criteria-page__header">
        <h1><mat-icon>tune</mat-icon> Critères de matching</h1>
        <p>Définissez les critères pondérés pour classer les candidats sur cette offre.</p>
        <button mat-stroked-button (click)="autoSuggest()" [disabled]="loading()">
          <mat-icon>auto_awesome</mat-icon> Auto-suggestion depuis les compétences de l'offre
        </button>
      </div>

      <!-- Suggestions -->
      @if (suggestions().length > 0) {
        <mat-card class="criteria-page__suggestions">
          <mat-card-header>
            <mat-card-title>Compétences suggérées</mat-card-title>
            <mat-card-subtitle>Cliquez pour ajouter</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <mat-chip-set>
              @for (s of suggestions(); track s.nom) {
                <mat-chip (click)="addFromSuggestion(s)" class="suggestion-chip">
                  {{ s.nom }} ({{ (s.poids * 100).toFixed(0) }}%)
                </mat-chip>
              }
            </mat-chip-set>
          </mat-card-content>
        </mat-card>
      }

      <!-- Liste des critères existants -->
      <mat-card>
        <mat-card-header>
          <mat-card-title>Critères définis ({{ criteres().length }})</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (criteres().length === 0) {
            <p class="criteria-page__empty">Aucun critère défini. Ajoutez des critères ci-dessous.</p>
          }
          @for (c of criteres(); track c.id) {
            <div class="criteria-row">
              <span class="criteria-row__name">{{ c.nom }}</span>
              <span class="criteria-row__weight">Importance : {{ (c.poids * 100).toFixed(0) }}%</span>
              @if (c.obligatoire) {
                <span class="criteria-row__mandatory">Obligatoire</span>
              }
              <button mat-icon-button color="warn" (click)="delete(c.id)" matTooltip="Supprimer">
                <mat-icon>delete</mat-icon>
              </button>
            </div>
          }
        </mat-card-content>
      </mat-card>

      <!-- Formulaire d'ajout -->
      <mat-card class="criteria-page__form">
        <mat-card-header>
          <mat-card-title>Ajouter un critère</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Nom du critère (ex. : Java, Spring Boot)</mat-label>
            <input matInput [(ngModel)]="newNom" />
          </mat-form-field>

          <div class="criteria-page__slider">
            <label><strong>Poids (Importance dans le score) : {{ (newPoids * 100).toFixed(0) }}%</strong></label>
            <input type="range" min="0.05" max="1" step="0.05" [(ngModel)]="newPoids" />
            <p class="criteria-page__hint">
              💡 Le <strong>poids</strong> définit le coefficient de cette compétence dans la note globale (de 5% à 100%). Plus il est élevé, plus cette compétence compte pour le classement des candidats.
            </p>
          </div>

          <mat-checkbox [(ngModel)]="newObligatoire">
            Critère obligatoire (filtre éliminatoire — exclut si absent du profil)
          </mat-checkbox>
        </mat-card-content>
        <mat-card-actions align="end">
          <button mat-flat-button color="primary" (click)="add()" [disabled]="!newNom.trim()">
            <mat-icon>add</mat-icon> Ajouter
          </button>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .criteria-page {
      max-width: 800px; margin: 0 auto; padding: 24px;
      display: flex; flex-direction: column; gap: 20px;
    }
    .criteria-page__header {
      h1 { display: flex; align-items: center; gap: 8px; margin: 0 0 8px; }
      p { color: #666; margin: 0 0 16px; }
    }
    .criteria-page__suggestions .suggestion-chip { cursor: pointer; }
    .criteria-page__empty { color: #888; text-align: center; padding: 16px; }
    .criteria-row {
      display: flex; align-items: center; gap: 12px;
      padding: 10px 0; border-bottom: 1px solid #eee;
      &:last-child { border-bottom: none; }
    }
    .criteria-row__name  { flex: 1; font-weight: 500; }
    .criteria-row__weight { color: #3f51b5; font-weight: 600; min-width: 40px; }
    .criteria-row__mandatory {
      background: #fff3e0; color: #bf360c;
      padding: 2px 8px; border-radius: 8px; font-size: 0.75rem;
    }
    .criteria-page__slider { margin: 16px 0; label { display: block; margin-bottom: 8px; } }
    .criteria-page__hint { font-size: 0.82rem; color: #555; margin: 6px 0 12px; line-height: 1.35; }
    .full-width { width: 100%; }
  `],
})
export class MatchingCriteria implements OnInit {
  private readonly route   = inject(ActivatedRoute);
  private readonly service = inject(MatchingService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading     = signal(false);
  readonly criteres    = signal<MatchingCritere[]>([]);
  readonly suggestions = signal<MatchingCritere[]>([]);

  newNom        = '';
  newPoids      = 0.25;
  newObligatoire = false;
  private offreId!: number;

  ngOnInit(): void {
    this.offreId = Number(this.route.snapshot.paramMap.get('offreId'));
    this.load();
  }

  private load(): void {
    this.service.getCriteres(this.offreId).subscribe({
      next: (c) => this.criteres.set(c),
    });
  }

  autoSuggest(): void {
    this.service.suggestCriteres(this.offreId).subscribe({
      next: (s) => this.suggestions.set(s),
    });
  }

  add(): void {
    if (!this.newNom.trim()) return;
    this.service.createCritere({
      offreId: this.offreId,
      nom: this.newNom.trim(),
      poids: this.newPoids,
      obligatoire: this.newObligatoire,
    }).subscribe({
      next: (c) => {
        this.criteres.update(list => [...list, c]);
        this.newNom = ''; this.newPoids = 0.25; this.newObligatoire = false;
        this.snackBar.open('✅ Critère ajouté', 'Fermer', { duration: 2000 });
      },
    });
  }

  addFromSuggestion(s: MatchingCritere): void {
    this.newNom   = s.nom;
    this.newPoids = s.poids;
    this.suggestions.update(list => list.filter(x => x.nom !== s.nom));
  }

  delete(id: number): void {
    this.service.deleteCritere(id).subscribe({
      next: () => {
        this.criteres.update(list => list.filter(c => c.id !== id));
        this.snackBar.open('Critère supprimé', 'Fermer', { duration: 2000 });
      },
    });
  }
}
