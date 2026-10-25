import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatchingService } from '../../../core/services/matching.service';
import { CandidatProfile } from '../../../core/models/matching.model';

/**
 * Page de profil candidat du module matching.
 * Permet de :
 * - Coller le texte de son CV
 * - Uploader un PDF
 * - Visualiser les compétences extraites automatiquement
 * - Voir et modifier son niveau d'expérience
 */
@Component({
  selector: 'app-matching-profile',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatButtonModule, MatCardModule, MatChipsModule,
    MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, MatSelectModule,
    MatSnackBarModule, MatTooltipModule,
  ],
  template: `
    <div class="matching-profile">
      <div class="matching-profile__header">
        <h1><mat-icon>person_search</mat-icon> Mon profil de matching</h1>
        <p class="matching-profile__subtitle">
          Collez votre CV ou uploadez un PDF pour que l'IA extrait vos compétences
          et calcule votre score de matching avec les offres disponibles.
        </p>
      </div>

      @if (loading()) {
        <div class="matching-profile__spinner">
          <mat-spinner diameter="48" />
          <p>Chargement…</p>
        </div>
      }

      @if (!loading()) {
        <div class="matching-profile__grid">
          <!-- Colonne gauche : saisie CV -->
          <mat-card class="matching-profile__card">
            <mat-card-header>
              <mat-icon mat-card-avatar>description</mat-icon>
              <mat-card-title>Votre CV</mat-card-title>
              <mat-card-subtitle>Collez le texte brut ou uploadez un PDF</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Texte du CV</mat-label>
                <textarea matInput [(ngModel)]="cvText" rows="14"
                          placeholder="Collez ici le texte de votre CV…"></textarea>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Niveau d'expérience</mat-label>
                <mat-select [(ngModel)]="niveau">
                  <mat-option value="JUNIOR">Junior</mat-option>
                  <mat-option value="INTERMEDIAIRE">Intermédiaire</mat-option>
                  <mat-option value="SENIOR">Senior</mat-option>
                  <mat-option value="EXPERT">Expert</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Résumé professionnel (optionnel)</mat-label>
                <textarea matInput [(ngModel)]="resume" rows="3"
                          placeholder="2 à 3 phrases sur votre profil…"></textarea>
              </mat-form-field>
            </mat-card-content>
            <mat-card-actions align="end">
              <button mat-button (click)="triggerFileInput()">
                <mat-icon>upload_file</mat-icon> Uploader PDF
              </button>
              <input #fileInput type="file" accept=".pdf,.txt" hidden
                     (change)="onFileSelected($event)" />
              <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
                @if (saving()) { <mat-spinner diameter="20" /> }
                @else { Enregistrer }
              </button>
            </mat-card-actions>
          </mat-card>

          <!-- Colonne droite : compétences extraites -->
          <mat-card class="matching-profile__card">
            <mat-card-header>
              <mat-icon mat-card-avatar>psychology</mat-icon>
              <mat-card-title>Compétences détectées</mat-card-title>
              <mat-card-subtitle>Extraites automatiquement par l'IA (Layer 0)</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              @if (profile() && profile()!.skills.length > 0) {
                <mat-chip-set class="matching-profile__chips">
                  @for (skill of profile()!.skills; track skill) {
                    <mat-chip color="primary" highlighted>{{ skill }}</mat-chip>
                  }
                </mat-chip-set>
              } @else {
                <div class="matching-profile__empty">
                  <mat-icon>info</mat-icon>
                  <p>Enregistrez votre CV pour voir les compétences extraites.</p>
                </div>
              }
            </mat-card-content>

            @if (profile()) {
              <mat-card-footer class="matching-profile__meta">
                <span>Niveau : <strong>{{ profile()!.niveau || 'Non renseigné' }}</strong></span>
                <span>Dernière mise à jour : {{ profile()!.updatedAt | date:'dd/MM/yyyy HH:mm' }}</span>
              </mat-card-footer>
            }
          </mat-card>
        </div>
      }
    </div>
  `,
  styles: [`
    .matching-profile {
      max-width: 1100px;
      margin: 0 auto;
      padding: 24px;
    }
    .matching-profile__header {
      margin-bottom: 24px;
      h1 {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 1.6rem;
        font-weight: 600;
        margin: 0 0 8px;
      }
    }
    .matching-profile__subtitle { color: #666; margin: 0; }
    .matching-profile__spinner {
      display: flex; flex-direction: column; align-items: center; padding: 48px;
    }
    .matching-profile__grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
      @media (max-width: 768px) { grid-template-columns: 1fr; }
    }
    .matching-profile__card { height: fit-content; }
    .full-width { width: 100%; }
    .matching-profile__chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .matching-profile__empty {
      display: flex; align-items: center; gap: 8px; color: #888; padding: 16px 0;
    }
    .matching-profile__meta {
      display: flex; justify-content: space-between; padding: 12px 16px;
      font-size: 0.8rem; color: #666; border-top: 1px solid #eee;
    }
  `],
})
export class MatchingProfile implements OnInit {
  private readonly matchingService = inject(MatchingService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(true);
  readonly saving  = signal(false);
  readonly profile = signal<CandidatProfile | null>(null);

  cvText  = '';
  niveau  = 'INTERMEDIAIRE';
  resume  = '';

  ngOnInit(): void {
    this.matchingService.getMonProfil().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.cvText  = p.cvTextPreview ?? '';
        this.niveau  = p.niveau ?? 'INTERMEDIAIRE';
        this.resume  = p.resumeProfessionnel ?? '';
        this.loading.set(false);
      },
      error: () => {
        // Pas encore de profil : formulaire vide
        this.loading.set(false);
      },
    });
  }

  save(): void {
    this.saving.set(true);
    this.matchingService.saveMonProfil({
      cvText: this.cvText,
      niveau: this.niveau,
      resumeProfessionnel: this.resume,
    }).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.saving.set(false);
        this.snackBar.open('✅ Profil enregistré et compétences extraites !', 'Fermer', { duration: 3000 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open("❌ Erreur lors de l'enregistrement", 'Fermer', { duration: 4000 });
      },
    });
  }

  triggerFileInput(): void {
    document.querySelector<HTMLInputElement>('input[type=file]')?.click();
  }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.saving.set(true);
    this.matchingService.uploadPdf(file, this.niveau).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.cvText = p.cvTextPreview ?? '';
        this.saving.set(false);
        this.snackBar.open('✅ PDF traité et compétences extraites !', 'Fermer', { duration: 3000 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('❌ Erreur lors du traitement du PDF', 'Fermer', { duration: 4000 });
      },
    });
  }
}
