import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { JobOfferService } from '../../../core/services/job-offer.service';
import { Candidature, OffreEmploi } from '../../../core/models/job-offer.model';

@Component({
  selector: 'app-offer-applications',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatExpansionModule,
    MatDividerModule,
  ],
  templateUrl: './offer-applications.html',
  styleUrl: './offer-applications.scss',
})
export class OfferApplications implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly jobOfferService = inject(JobOfferService);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly offre = signal<OffreEmploi | null>(null);
  protected readonly candidatures = signal<Candidature[]>([]);

  private offreId = 0;

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.offreId = idParam ? parseInt(idParam, 10) : 0;
    this.loadData();
  }

  private loadData(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.jobOfferService.getById(this.offreId).subscribe({
      next: (offre) => {
        this.offre.set(offre);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger les données de l\'offre.');
      },
    });

    this.jobOfferService.getCandidatures(this.offreId).subscribe({
      next: (list) => {
        this.candidatures.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger les candidatures.');
        this.loading.set(false);
      },
    });
  }

  protected backToOffers(): void {
    void this.router.navigate(['/app/recruteur/offres']);
  }

  protected parseJsonList(jsonString: string | null): string[] {
    if (!jsonString) return [];
    try {
      const parsed = JSON.parse(jsonString);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [jsonString];
    }
  }

  protected getScoreClass(score: number | null): string {
    if (score === null || score === undefined) return 'score-na';
    if (score >= 75) return 'score-high';
    if (score >= 50) return 'score-medium';
    return 'score-low';
  }
}
