import { Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  MAX_OFFRES_COMPAREES,
  OffreComparaisonService,
  OffreSelectionnable,
} from '../../../core/services/offre-comparaison.service';

/** Bouton « Comparer » d'une carte d'offre (Module 2 - comparaison IA). */
@Component({
  selector: 'app-compare-toggle',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <button
      mat-stroked-button
      type="button"
      class="compare-toggle"
      [class.compare-toggle--on]="selectionnee()"
      [disabled]="!selectionnee() && comparaison.pleine()"
      [matTooltip]="!selectionnee() && comparaison.pleine() ? 'Maximum ' + max + ' offres' : ''"
      [attr.aria-pressed]="selectionnee()"
      (click)="comparaison.basculer(offre())"
    >
      <mat-icon aria-hidden="true">{{ selectionnee() ? 'check_box' : 'compare_arrows' }}</mat-icon>
      {{ selectionnee() ? 'Sélectionnée' : 'Comparer' }}
    </button>
  `,
  styles: `
    .compare-toggle--on {
      background: #dbeafe;
      color: #1e3a8a;
      border-color: #1e3a8a;
    }
  `,
})
export class CompareToggle {
  protected readonly comparaison = inject(OffreComparaisonService);
  protected readonly max = MAX_OFFRES_COMPAREES;

  readonly offre = input.required<OffreSelectionnable>();
  protected readonly selectionnee = computed(() =>
    this.comparaison.selectionnees().some((o) => o.id === this.offre().id),
  );
}
