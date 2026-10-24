import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth.service';
import { RoleNavigationService } from '../../../core/services/role-navigation.service';

@Component({
  selector: 'app-access-denied',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './access-denied.html',
  styleUrl: './access-denied.scss',
})
export class AccessDenied {
  protected readonly authService = inject(AuthService);
  private readonly roleNavigation = inject(RoleNavigationService);

  protected readonly homePath = computed(() =>
    this.roleNavigation.homePathForCurrentUser(),
  );
  protected readonly profilePath = computed(() =>
    this.roleNavigation.profilePathForCurrentUser('view'),
  );
}
