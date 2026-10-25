import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable } from 'rxjs';
import {
  OffreDetaillee,
  OffreStatistiques,
  STATUT_LABELS,
  StatutOffre,
} from '../../../core/models/offre-gestion.model';
import { OffreGestionService, messageErreur } from '../../../core/services/offre-gestion.service';

/**
 * Module 2 : modération des offres par l'administrateur.
 * L'admin consulte, suspend (avec motif), réactive ou supprime, mais ne modifie jamais le contenu.
 */
@Component({
  selector: 'app-admin-offres',
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
    MatTooltipModule,
  ],
  templateUrl: './admin-offres.html',
  styleUrls: ['../offres-shared.scss', './admin-offres.scss'],
})
export class AdminOffres implements OnInit {
  private readonly service = inject(OffreGestionService);

  protected readonly statutLabels = STATUT_LABELS;
  protected readonly statuts = Object.keys(STATUT_LABELS) as StatutOffre[];

  protected readonly offres = signal<OffreDetaillee[]>([]);
  protected readonly stats = signal<OffreStatistiques | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly enSuspension = signal<OffreDetaillee | null>(null);

  protected q = '';
  protected statut: StatutOffre | '' = '';
  protected motif = '';

  ngOnInit(): void {
    this.charger();
  }

  protected filtrerStatut(s: StatutOffre | ''): void {
    this.statut = s;
    this.rechercher();
  }

  protected rechercher(): void {
    this.page.set(0);
    this.charger();
  }

  protected allerPage(delta: number): void {
    this.page.update((p) => p + delta);
    this.charger();
  }

  protected ouvrirSuspension(o: OffreDetaillee): void {
    this.enSuspension.set(o);
    this.motif = '';
  }

  protected confirmerSuspension(): void {
    const o = this.enSuspension();
    if (!o) return;
    if (this.motif.trim().length < 5) {
      this.error.set('Indiquez un motif de suspension clair (5 caractères minimum).');
      return;
    }
    this.action(this.service.adminSuspendre(o.id, this.motif.trim()), `« ${o.titrePoste} » est suspendue.`);
    this.enSuspension.set(null);
  }

  protected reactiver(o: OffreDetaillee): void {
    this.action(this.service.adminReactiver(o.id), `« ${o.titrePoste} » est réactivée et de nouveau publiée.`);
  }

  protected supprimer(o: OffreDetaillee): void {
    if (confirm(`Supprimer définitivement « ${o.titrePoste} » ? Cette action est irréversible.`)) {
      this.action(this.service.adminSupprimer(o.id), `« ${o.titrePoste} » a été supprimée.`);
    }
  }

  private action(obs: Observable<unknown>, message: string): void {
    this.error.set(null);
    this.success.set(null);
    obs.subscribe({
      next: () => {
        this.success.set(message);
        this.charger();
      },
      error: (err) => this.error.set(messageErreur(err, "L'action a échoué.")),
    });
  }

  private charger(): void {
    this.loading.set(true);
    this.service.adminStatistiques().subscribe({ next: (s) => this.stats.set(s), error: () => this.stats.set(null) });
    this.service.adminLister({ q: this.q, statut: this.statut }, this.page()).subscribe({
      next: (res) => {
        this.offres.set(res.content);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(messageErreur(err, 'Impossible de charger les offres.'));
        this.loading.set(false);
      },
    });
  }
}
