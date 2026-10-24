import { Component, input } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-loading-spinner',
  imports: [MatProgressSpinnerModule],
  template: `
    <div class="loading" role="status" aria-live="polite">
      <mat-progress-spinner
        mode="indeterminate"
        [diameter]="diameter()"
        aria-label="Chargement en cours"
      />
      <span class="loading__message">{{ message() }}</span>
    </div>
  `,
  styleUrl: './loading-spinner.scss',
})
export class LoadingSpinner {
  readonly message = input('Chargement en cours…');
  readonly diameter = input(44);
}
