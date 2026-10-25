import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth.service';
import { RoleNavigationService } from '../../../core/services/role-navigation.service';
import { User } from '../../../core/models/user.model';
import { getApiErrorMessage } from '../../../core/utils/http-error.util';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { LoadingSpinner } from '../../../shared/components/loading-spinner/loading-spinner';
import { PageHeader } from '../../../shared/components/page-header/page-header';
import { RoleLabelPipe } from '../../../shared/pipes/role-label.pipe';

@Component({
  selector: 'app-profile-view',
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    DatePipe,
    PageHeader,
    LoadingSpinner,
    EmptyState,
    RoleLabelPipe,
  ],
  templateUrl: './profile-view.html',
  styleUrl: './profile-view.scss',
})
export class ProfileView {
  private readonly authService = inject(AuthService);
  private readonly roleNavigation = inject(RoleNavigationService);

  protected readonly user = signal<User | null>(this.authService.currentUser());
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly editPath = computed(() =>
    this.roleNavigation.profilePathForCurrentUser('edit'),
  );
  protected readonly passwordPath = computed(() =>
    this.roleNavigation.profilePathForCurrentUser('change-password'),
  );
  protected readonly homePath = computed(() =>
    this.roleNavigation.homePathForCurrentUser(),
  );

  protected readonly initials = computed(() => {
    const user = this.user();
    return user
      ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase()
      : '?';
  });

  constructor() {
    this.loadProfile();
  }

  protected loadProfile(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.getCurrentUser(true).subscribe({
      next: (user) => {
        this.user.set(user);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          getApiErrorMessage(error, 'Impossible de charger votre profil.'),
        );
      },
    });
  }
}
