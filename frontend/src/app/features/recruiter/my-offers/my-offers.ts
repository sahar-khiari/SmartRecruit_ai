import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { JobOfferService } from '../../../core/services/job-offer.service';
import { PagedResponse } from '../../../core/models/job-offer.model';
import { OffreDetaillee, STATUT_LABELS, StatutOffre } from '../../../core/models/offre-gestion.model';
import { OffreGestionService, messageErreur } from '../../../core/services/offre-gestion.service';

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
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './my-offers.html',
  styleUrl: './my-offers.scss',
})
export class MyOffers implements OnInit {
  private readonly jobOfferService = inject(JobOfferService);
  private readonly offreGestionService = inject(OffreGestionService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly offres = signal<OffreDetaillee[]>([]);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly currentPage = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly pageSize = 10;

  // Module 2 : filtres et cycle de vie
  protected readonly statutLabels = STATUT_LABELS;
  protected readonly statuts = Object.keys(STATUT_LABELS) as StatutOffre[];
  protected recherche = '';
  protected statutFiltre: StatutOffre | '' = '';

  ngOnInit(): void {
    this.loadMesOffres();
  }

  protected loadMesOffres(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.offreGestionService
      .rechercherMesOffres({ q: this.recherche, statut: this.statutFiltre }, this.currentPage(), this.pageSize)
      .subscribe({
      next: (response: PagedResponse<OffreDetaillee>) => {
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

  protected filtrer(): void {
    this.currentPage.set(0);
    this.loadMesOffres();
  }

  protected publier(offre: OffreDetaillee, event: Event): void {
    event.stopPropagation();
    this.changerStatut(this.offreGestionService.publier(offre.id), `« ${offre.titrePoste} » est publiée.`);
  }

  protected cloturer(offre: OffreDetaillee, event: Event): void {
    event.stopPropagation();
    this.changerStatut(this.offreGestionService.cloturer(offre.id), `« ${offre.titrePoste} » est clôturée.`);
  }

  protected archiver(offre: OffreDetaillee, event: Event): void {
    event.stopPropagation();
    if (confirm(`Archiver « ${offre.titrePoste} » ? Elle ne pourra plus être modifiée.`)) {
      this.changerStatut(this.offreGestionService.archiver(offre.id), `« ${offre.titrePoste} » est archivée.`);
    }
  }

  private changerStatut(action$: Observable<OffreDetaillee>, message: string): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    action$.subscribe({
      next: (updated) => {
        this.offres.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
        this.successMessage.set(message);
      },
      error: (err) => this.errorMessage.set(messageErreur(err, 'Impossible de modifier le statut de l’offre.')),
    });
  }

  protected deleteOffer(offre: OffreDetaillee, event: Event): void {
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

  protected editOffer(offre: OffreDetaillee): void {
    void this.router.navigate(['/app/recruteur/offres', offre.id, 'edit']);
  }

  protected viewApplications(offre: OffreDetaillee): void {
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
