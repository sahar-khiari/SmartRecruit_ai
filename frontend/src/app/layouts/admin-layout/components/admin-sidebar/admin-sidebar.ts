import { Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

interface FutureAdminModule {
  icon: string;
  label: string;
}

/**
 * Navigation latérale exclusive au back-office ADMIN.
 */
@Component({
  selector: 'app-admin-sidebar',
  imports: [RouterLink, RouterLinkActive, MatIconModule, MatTooltipModule],
  templateUrl: './admin-sidebar.html',
  styleUrl: './admin-sidebar.scss',
})
export class AdminSidebar {
  readonly collapsed = input(false);
  readonly navigated = output<void>();

  protected readonly futureModules: readonly FutureAdminModule[] = [
    { icon: 'groups', label: 'Candidats' },
    { icon: 'video_call', label: 'Entretiens' },
    { icon: 'hub', label: 'Matching' },
  ];
}
