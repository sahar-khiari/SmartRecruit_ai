import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-empty-state',
  imports: [MatIconModule],
  template: `
    <div class="empty-state">
      <span class="empty-state__icon" aria-hidden="true">
        <mat-icon>{{ icon() }}</mat-icon>
      </span>
      <h2 class="empty-state__title">{{ title() }}</h2>
      <p class="empty-state__message">{{ message() }}</p>
      <div class="empty-state__actions">
        <ng-content />
      </div>
    </div>
  `,
  styleUrl: './empty-state.scss',
})
export class EmptyState {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly message = input<string>('');
}
