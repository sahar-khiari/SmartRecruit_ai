import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { JobOfferService } from '../../../core/services/job-offer.service';
import { AuthService } from '../../../core/services/auth.service';
import { Candidature, OffreEmploi, PagedResponse } from '../../../core/models/job-offer.model';

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
  ],
  templateUrl: './job-offer-list.html',
  styleUrl: './job-offer-list.scss',
})
export class JobOfferList implements OnInit {
  private readonly jobOfferService = inject(JobOfferService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly offres = signal<OffreEmploi[]>([]);
  protected readonly candidaturesMap = signal<Record<number, Candidature>>({});
  protected readonly currentPage = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly pageSize = 10;

  protected readonly isCandidate = this.authService.currentUser()?.role === 'CANDIDATE';

  ngOnInit(): void {
    this.loadOffres();
  }

  protected loadOffres(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    if (this.isCandidate) {
      this.jobOfferService.getMesCandidatures().subscribe({
        next: (cands: Candidature[]) => {
          const map: Record<number, Candidature> = {};
          for (const cand of cands) {
            map[cand.offreId] = cand;
          }
          this.candidaturesMap.set(map);
        },
        error: () => {},
      });
    }

    this.jobOfferService.getOffres(this.currentPage(), this.pageSize).subscribe({
      next: (response: PagedResponse<OffreEmploi>) => {
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

  protected getCandidatureForOffer(offreId: number): Candidature | undefined {
    return this.candidaturesMap()[offreId];
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
