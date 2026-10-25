import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { JobOfferService } from '../../../core/services/job-offer.service';
import { AuthService } from '../../../core/services/auth.service';
import { OffreEmploi, PagedResponse } from '../../../core/models/job-offer.model';
import {
  MODE_TRAVAIL_LABELS,
  ModeTravail,
  OffreDetaillee,
  OffreRechercheFiltres,
} from '../../../core/models/offre-gestion.model';
import { OffreGestionService } from '../../../core/services/offre-gestion.service';

@Component({
  selector: 'app-job-offer-list',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './job-offer-list.html',
  styleUrl: './job-offer-list.scss',
})
export class JobOfferList implements OnInit {
  private readonly jobOfferService = inject(JobOfferService);
  private readonly offreGestionService = inject(OffreGestionService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly offres = signal<OffreDetaillee[]>([]);
  protected readonly currentPage = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly pageSize = 10;

  protected readonly isCandidate = this.authService.currentUser()?.role === 'CANDIDATE';

  // Module 2 : recherche multicritère
  protected readonly niveaux = ['Junior', 'Intermédiaire', 'Senior', 'Expert'];
  protected readonly contrats = ['CDI', 'CDD', 'Freelance', 'Stage', 'Alternance'];
  protected readonly modes = Object.entries(MODE_TRAVAIL_LABELS) as [ModeTravail, string][];
  protected readonly modeLabels = MODE_TRAVAIL_LABELS;
  protected filtres: OffreRechercheFiltres = this.filtresVides();

  ngOnInit(): void {
    this.loadOffres();
  }

  protected loadOffres(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.offreGestionService.rechercher(this.filtres, this.currentPage(), this.pageSize).subscribe({
      next: (response: PagedResponse<OffreDetaillee>) => {
        this.offres.set(response.content);
        this.totalPages.set(response.totalPages);
        this.totalElements.set(response.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger les offres d\'emploi. Vérifiez votre connexion.');
        this.loading.set(false);
      },
    });
  }

  protected rechercher(): void {
    this.currentPage.set(0);
    this.loadOffres();
  }

  protected reinitialiserFiltres(): void {
    this.filtres = this.filtresVides();
    this.rechercher();
  }

  protected salaire(offre: OffreDetaillee): string | null {
    if (offre.salaireMin && offre.salaireMax) return `${offre.salaireMin} – ${offre.salaireMax} TND`;
    if (offre.salaireMin) return `À partir de ${offre.salaireMin} TND`;
    if (offre.salaireMax) return `Jusqu'à ${offre.salaireMax} TND`;
    return null;
  }

  private filtresVides(): OffreRechercheFiltres {
    return { q: '', localisation: '', typeContrat: '', niveauRequis: '', modeTravail: '', competence: '', salaireMin: null };
  }

  protected previousPage(): void {
    if (this.currentPage() > 0) {
      this.currentPage.set(this.currentPage() - 1);
      this.loadOffres();
    }
  }

  protected nextPage(): void {
    if (this.currentPage() < this.totalPages() - 1) {
      this.currentPage.set(this.currentPage() + 1);
      this.loadOffres();
    }
  }

  protected applyToOffer(offre: OffreEmploi): void {
    void this.router.navigate(['/app/offres', offre.id, 'postuler']);
  }

  protected viewDetail(offre: OffreEmploi): void {
    void this.router.navigate(['/app/offres', offre.id, 'postuler']);
  }

  protected getCompetencesList(competences: string): string[] {
    return competences.split(',').map((s) => s.trim()).filter((s) => s.length > 0);
  }
}
