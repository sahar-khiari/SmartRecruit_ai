import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { UserMenu } from '../../../../shared/components/user-menu/user-menu';

/**
 * Barre supérieure du back-office ADMIN.
 */
@Component({
  selector: 'app-admin-topbar',
  imports: [MatToolbarModule, MatButtonModule, MatIconModule, UserMenu],
  templateUrl: './admin-topbar.html',
  styleUrl: './admin-topbar.scss',
})
export class AdminTopbar {
  readonly pageTitle = input<string>('Tableau de bord administratif');
  readonly menuToggle = output<void>();
}
