import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  OffreComparaison,
  OffreComparaisonService,
} from '../../../core/services/offre-comparaison.service';
import { getApiErrorMessage } from '../../../core/utils/http-error.util';

/** Ligne du tableau comparatif : un libellé et une valeur par offre. */
interface LigneComparaison {
  critere: string;
  valeurs: string[];
}

/** Résultat de la comparaison IA ; « Postuler » mène au parcours de candidature existant. */
@Component({
  selector: 'app-comparaison-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './comparaison-dialog.html',
  styleUrl: './comparaison-dialog.scss',
})
export class ComparaisonDialog implements OnInit {
  private readonly comparaisonService = inject(OffreComparaisonService);
  private readonly dialogRef = inject(MatDialogRef<ComparaisonDialog>);
  private readonly router = inject(Router);
  private readonly data = inject<{ profil: string }>(MAT_DIALOG_DATA);

  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly resultat = signal<OffreComparaison | null>(null);
  protected readonly lettres = ['A', 'B', 'C'];

  protected readonly lignes = computed<LigneComparaison[]>(() => {
    const offres = this.resultat()?.offres ?? [];
    return [
      { critere: 'Missions', valeurs: offres.map((o) => o.missions) },
      { critere: 'Niveau', valeurs: offres.map((o) => o.niveau) },
      { critere: 'Compétences clés', valeurs: offres.map((o) => o.competencesCles.join(', ')) },
      { critere: 'Contrat / Lieu', valeurs: offres.map((o) => `${o.contrat}, ${o.lieu}`) },
      { critere: 'Profil idéal', valeurs: offres.map((o) => o.profilIdeal || '—') },
    ];
  });

  ngOnInit(): void {
    this.lancer();
  }

  protected lancer(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.comparaisonService.comparer(this.data.profil ?? '').subscribe({
      next: (resultat) => {
        this.resultat.set(resultat);
        this.chargement.set(false);
      },
      error: (error) => {
        this.erreur.set(getApiErrorMessage(error, 'La comparaison a échoué. Réessayez dans quelques instants.'));
        this.chargement.set(false);
      },
    });
  }

  protected lettre(offreId: number): string {
    const index = this.resultat()?.offres.findIndex((o) => o.offreId === offreId) ?? -1;
    return index >= 0 ? this.lettres[index] : '';
  }

  protected postuler(offreId: number): void {
    this.dialogRef.close();
    void this.router.navigate(['/app/offres', offreId, 'postuler']);
  }
}
