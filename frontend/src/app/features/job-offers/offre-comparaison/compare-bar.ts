import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { OffreComparaisonService } from '../../../core/services/offre-comparaison.service';
import { ComparaisonDialog } from './comparaison-dialog';

/**
 * Barre fixée en bas de la liste des offres : nombre d'offres sélectionnées, profil facultatif,
 * bouton « Comparer avec l'IA » (Module 2).
 */
@Component({
  selector: 'app-compare-bar',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule],
  templateUrl: './compare-bar.html',
  styleUrl: './compare-bar.scss',
})
export class CompareBar {
  protected readonly comparaison = inject(OffreComparaisonService);
  private readonly dialog = inject(MatDialog);

  protected readonly profilOuvert = signal(false);
  protected profil = '';

  protected comparer(): void {
    this.dialog.open(ComparaisonDialog, {
      data: { profil: this.profil },
      width: '980px',
      maxWidth: '96vw',
      autoFocus: 'dialog',
    });
  }
}
