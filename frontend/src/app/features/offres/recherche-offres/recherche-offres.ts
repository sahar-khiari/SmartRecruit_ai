import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RoleNavigationService } from '../../../core/services/role-navigation.service';
import {
  MODE_TRAVAIL_LABELS,
  ModeTravail,
  OffreDetaillee,
  OffreRechercheFiltres,
} from '../../../core/models/offre-gestion.model';
import { OffreGestionService, messageErreur, splitListe } from '../../../core/services/offre-gestion.service';

/**
 * Module 2 : recherche multicritère des offres publiées (candidats et recruteurs).
 */
@Component({
  selector: 'app-recherche-offres',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './recherche-offres.html',
  styleUrl: '../offres-shared.scss',
})
export class RechercheOffres implements OnInit {
  private readonly service = inject(OffreGestionService);
  private readonly router = inject(Router);
  private readonly roleNavigation = inject(RoleNavigationService);

  protected readonly niveaux = ['Junior', 'Intermédiaire', 'Senior', 'Expert'];
  protected readonly contrats = ['CDI', 'CDD', 'Stage', 'Alternance', 'Freelance'];
  protected readonly modes = Object.entries(MODE_TRAVAIL_LABELS) as [ModeTravail, string][];
  protected readonly modeLabels = MODE_TRAVAIL_LABELS;
  protected readonly splitListe = splitListe;

  protected readonly offres = signal<OffreDetaillee[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);

  protected filtres: OffreRechercheFiltres = this.filtresVides();

  protected get isCandidate(): boolean {
    return this.roleNavigation.currentRole() === 'CANDIDATE';
  }

  ngOnInit(): void {
    this.charger();
  }

  protected rechercher(): void {
    this.page.set(0);
    this.charger();
  }

  protected reinitialiser(): void {
    this.filtres = this.filtresVides();
    this.rechercher();
  }

  protected allerPage(delta: number): void {
    this.page.update((p) => p + delta);
    this.charger();
  }

  protected postuler(o: OffreDetaillee): void {
    void this.router.navigate(['/app/offres', o.id, 'postuler']);
  }

  protected salaire(o: OffreDetaillee): string | null {
    if (o.salaireMin && o.salaireMax) return `${o.salaireMin} – ${o.salaireMax} TND`;
    if (o.salaireMin) return `À partir de ${o.salaireMin} TND`;
    if (o.salaireMax) return `Jusqu'à ${o.salaireMax} TND`;
    return null;
  }

  private charger(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.rechercher(this.filtres, this.page()).subscribe({
      next: (res) => {
        this.offres.set(res.content);
        this.totalPages.set(res.totalPages);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(messageErreur(err, 'Impossible de charger les offres.'));
        this.loading.set(false);
      },
    });
  }

  private filtresVides(): OffreRechercheFiltres {
    return { q: '', localisation: '', typeContrat: '', niveauRequis: '', modeTravail: '', competence: '', salaireMin: null };
  }
}
