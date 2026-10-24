import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../../core/services/auth.service';
import { RoleNavigationService } from '../../../core/services/role-navigation.service';

@Component({
  selector: 'app-public-navbar',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './public-navbar.html',
  styleUrl: './public-navbar.scss',
})
export class PublicNavbar {
  protected readonly authService = inject(AuthService);
  private readonly roleNavigation = inject(RoleNavigationService);

  protected readonly homePath = computed(() =>
    this.roleNavigation.homePathForCurrentUser(),
  );
}
