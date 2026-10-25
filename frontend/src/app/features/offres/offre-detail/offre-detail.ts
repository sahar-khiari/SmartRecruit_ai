import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, Location } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RoleNavigationService } from '../../../core/services/role-navigation.service';
import { MODE_TRAVAIL_LABELS, OffreDetaillee, STATUT_LABELS } from '../../../core/models/offre-gestion.model';
import { OffreGestionService, messageErreur, splitListe } from '../../../core/services/offre-gestion.service';

/**
 * Module 2 : détail d'une offre. Route /app/offres/:id (candidat, recruteur) et
 * /admin/offres/:id (lecture seule, data.admin = true).
 */
@Component({
  selector: 'app-offre-detail',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './offre-detail.html',
  styleUrls: ['../offres-shared.scss', './offre-detail.scss'],
})
export class OffreDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly service = inject(OffreGestionService);
  private readonly roleNavigation = inject(RoleNavigationService);

  protected readonly statutLabels = STATUT_LABELS;
  protected readonly modeLabels = MODE_TRAVAIL_LABELS;
  protected readonly splitListe = splitListe;
  protected readonly isAdminView = this.route.snapshot.data['admin'] === true;

  protected readonly offre = signal<OffreDetaillee | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected get role(): string | null {
    return this.roleNavigation.currentRole();
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    const source$ = this.isAdminView ? this.service.adminDetail(id) : this.service.getById(id);
    source$.subscribe({
      next: (o) => {
        this.offre.set(o);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(messageErreur(err, 'Offre introuvable.'));
        this.loading.set(false);
      },
    });
  }

  protected postuler(o: OffreDetaillee): void {
    void this.router.navigate(['/app/offres', o.id, 'postuler']);
  }

  protected retour(): void {
    this.location.back();
  }
}
