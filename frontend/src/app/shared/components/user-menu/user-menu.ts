import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../../core/services/auth.service';
import { RoleNavigationService } from '../../../core/services/role-navigation.service';
import { RoleLabelPipe } from '../../pipes/role-label.pipe';

/**
 * Menu utilisateur partagé par le back-office et le front-office.
 * Les liens restent dans l'espace correspondant au rôle connecté.
 */
@Component({
  selector: 'app-user-menu',
  imports: [RouterLink, MatDividerModule, MatIconModule, MatMenuModule, RoleLabelPipe],
  templateUrl: './user-menu.html',
  styleUrl: './user-menu.scss',
})
export class UserMenu {
  readonly compact = input(false);
  readonly menuPosition = input<'before' | 'after'>('after');

  protected readonly authService = inject(AuthService);
  private readonly roleNavigation = inject(RoleNavigationService);

  protected readonly initials = computed(() => {
    const user = this.authService.currentUser();
    if (!user) {
      return '?';
    }
    return `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase();
  });

  protected readonly profilePath = computed(() =>
    this.roleNavigation.profilePathForCurrentUser('view'),
  );
  protected readonly editPath = computed(() =>
    this.roleNavigation.profilePathForCurrentUser('edit'),
  );
  protected readonly passwordPath = computed(() =>
    this.roleNavigation.profilePathForCurrentUser('change-password'),
  );

  protected logout(): void {
    this.authService.logout();
  }
}
