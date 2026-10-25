import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { JobOfferService } from '../../../core/services/job-offer.service';
import { OffreEmploi, PagedResponse } from '../../../core/models/job-offer.model';

@Component({
  selector: 'app-my-offers',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatTooltipModule,
    MatDividerModule,
  ],
  templateUrl: './my-offers.html',
  styleUrl: './my-offers.scss',
})
export class MyOffers implements OnInit {
  private readonly jobOfferService = inject(JobOfferService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly offres = signal<OffreEmploi[]>([]);
  protected readonly currentPage = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly pageSize = 10;

  ngOnInit(): void {
    this.loadMesOffres();
  }

  protected loadMesOffres(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.jobOfferService.getMesOffres(this.currentPage(), this.pageSize).subscribe({
      next: (response: PagedResponse<OffreEmploi>) => {
        this.offres.set(response.content);
        this.totalPages.set(response.totalPages);
        this.totalElements.set(response.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger vos offres. Vérifiez votre connexion.');
        this.loading.set(false);
      },
    });
  }

  protected toggleActive(offre: OffreEmploi, event: Event): void {
    event.stopPropagation();
    const newStatus = !offre.active;
    this.jobOfferService.toggleStatus(offre.id, newStatus).subscribe({
      next: (updated) => {
        this.offres.update((list) =>
          list.map((item) => (item.id === updated.id ? updated : item))
        );
      },
      error: () => {
        this.errorMessage.set('Impossible de modifier le statut de l\'offre.');
      },
    });
  }

  protected deleteOffer(offre: OffreEmploi, event: Event): void {
    event.stopPropagation();
    if (!confirm(`Êtes-vous sûr de vouloir supprimer l'offre "${offre.titrePoste}" ?`)) {
      return;
    }
    this.jobOfferService.delete(offre.id).subscribe({
      next: () => {
        this.loadMesOffres();
      },
      error: () => {
        this.errorMessage.set('Impossible de supprimer l\'offre.');
      },
    });
  }

  protected createOffer(): void {
    void this.router.navigate(['/app/recruteur/offres/new']);
  }

  protected editOffer(offre: OffreEmploi): void {
    void this.router.navigate(['/app/recruteur/offres', offre.id, 'edit']);
  }

  protected viewApplications(offre: OffreEmploi): void {
    void this.router.navigate(['/app/recruteur/offres', offre.id, 'candidatures']);
  }

  protected previousPage(): void {
    if (this.currentPage() > 0) {
      this.currentPage.set(this.currentPage() - 1);
      this.loadMesOffres();
    }
  }

  protected nextPage(): void {
    if (this.currentPage() < this.totalPages() - 1) {
      this.currentPage.set(this.currentPage() + 1);
      this.loadMesOffres();
    }
  }
}
