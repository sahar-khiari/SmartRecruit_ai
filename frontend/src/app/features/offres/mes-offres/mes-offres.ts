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
import { OffreDetaillee, STATUT_LABELS, StatutOffre } from '../../../core/models/offre-gestion.model';
import { OffreGestionService, messageErreur } from '../../../core/services/offre-gestion.service';

/**
 * Module 2 : liste des offres du recruteur connecté, avec filtres et actions du cycle de vie.
 */
@Component({
  selector: 'app-mes-offres',
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
  templateUrl: './mes-offres.html',
  styleUrl: '../offres-shared.scss',
})
export class MesOffres implements OnInit {
  private readonly service = inject(OffreGestionService);

  protected readonly statutLabels = STATUT_LABELS;
  protected readonly statuts = Object.keys(STATUT_LABELS) as StatutOffre[];

  protected readonly offres = signal<OffreDetaillee[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);

  protected q = '';
  protected statut: StatutOffre | '' = '';

  ngOnInit(): void {
    this.charger();
  }

  protected rechercher(): void {
    this.page.set(0);
    this.charger();
  }

  protected reinitialiser(): void {
    this.q = '';
    this.statut = '';
    this.rechercher();
  }

  protected allerPage(delta: number): void {
    this.page.update((p) => p + delta);
    this.charger();
  }

  protected publier(o: OffreDetaillee): void {
    this.action(this.service.publier(o.id), `« ${o.titrePoste} » est maintenant publiée.`);
  }

  protected cloturer(o: OffreDetaillee): void {
    this.action(this.service.cloturer(o.id), `« ${o.titrePoste} » est clôturée.`);
  }

  protected archiver(o: OffreDetaillee): void {
    if (confirm(`Archiver « ${o.titrePoste} » ? Elle ne pourra plus être modifiée.`)) {
      this.action(this.service.archiver(o.id), `« ${o.titrePoste} » est archivée.`);
    }
  }

  protected supprimer(o: OffreDetaillee): void {
    if (confirm(`Supprimer définitivement « ${o.titrePoste} » ?`)) {
      this.action(this.service.delete(o.id), `« ${o.titrePoste} » a été supprimée.`);
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
    this.service.rechercherMesOffres({ q: this.q, statut: this.statut }, this.page()).subscribe({
      next: (res) => {
        this.offres.set(res.content);
        this.totalPages.set(res.totalPages);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(messageErreur(err, 'Impossible de charger vos offres.'));
        this.loading.set(false);
      },
    });
  }
}
