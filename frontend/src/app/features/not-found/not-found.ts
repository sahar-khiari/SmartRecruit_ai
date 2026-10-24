import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/services/auth.service';
import { RoleNavigationService } from '../../core/services/role-navigation.service';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './not-found.html',
  styleUrl: './not-found.scss',
})
export class NotFound {
  protected readonly authService = inject(AuthService);
  private readonly roleNavigation = inject(RoleNavigationService);

  protected readonly homePath = computed(() =>
    this.roleNavigation.homePathForCurrentUser(),
  );
}
