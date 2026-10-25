import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { JobOfferService } from '../../../core/services/job-offer.service';
import { OffreEmploiCreateRequest } from '../../../core/models/job-offer.model';

@Component({
  selector: 'app-create-offer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-offer.html',
  styleUrl: './create-offer.scss',
})
export class CreateOffer implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly jobOfferService = inject(JobOfferService);

  protected readonly isEditMode = signal(false);
  protected readonly offerId = signal<number | null>(null);
  protected readonly loading = signal(false);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  // Formulaire
  protected titrePoste = '';
  protected description = '';
  protected niveauRequis = 'Intermédiaire';
  protected typeContrat = 'CDI';
  protected localisation = 'Paris / Télétravail';
  protected competencesRequises = 'Python, FastAPI, Docker, SQL';
  protected dureeTestMinutes = 30;

  protected readonly niveaux = ['Junior', 'Intermédiaire', 'Senior', 'Expert'];
  protected readonly contrats = ['CDI', 'CDD', 'Freelance', 'Stage', 'Alternance'];

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = parseInt(idParam, 10);
      this.isEditMode.set(true);
      this.offerId.set(id);
      this.loadOfferForEdit(id);
    }
  }

  private loadOfferForEdit(id: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.jobOfferService.getById(id).subscribe({
      next: (offre) => {
        this.titrePoste = offre.titrePoste;
        this.description = offre.description;
        this.niveauRequis = offre.niveauRequis;
        this.typeContrat = offre.typeContrat;
        this.localisation = offre.localisation;
        this.competencesRequises = offre.competencesRequises;
        this.dureeTestMinutes = offre.dureeTestMinutes;
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger les données de l\'offre.');
        this.loading.set(false);
      },
    });
  }

  protected save(): void {
    if (!this.titrePoste.trim() || !this.description.trim() || !this.competencesRequises.trim()) {
      this.errorMessage.set('Veuillez remplir tous les champs obligatoires.');
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const payload: OffreEmploiCreateRequest = {
      titrePoste: this.titrePoste.trim(),
      description: this.description.trim(),
      niveauRequis: this.niveauRequis,
      typeContrat: this.typeContrat,
      localisation: this.localisation.trim(),
      competencesRequises: this.competencesRequises.trim(),
      dureeTestMinutes: this.dureeTestMinutes,
    };

    const action$ = this.isEditMode() && this.offerId()
      ? this.jobOfferService.update(this.offerId()!, payload)
      : this.jobOfferService.create(payload);

    action$.subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigate(['/app/recruteur/offres']);
      },
      error: (err) => {
        this.submitting.set(false);
        const detail =
          err.error?.message ||
          err.error?.error ||
          'Erreur lors de l\'enregistrement de l\'offre.';
        this.errorMessage.set(detail);
      },
    });
  }

  protected cancel(): void {
    void this.router.navigate(['/app/recruteur/offres']);
  }
}
