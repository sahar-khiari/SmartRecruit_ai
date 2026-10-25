import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth.service';
import { RoleLabelPipe } from '../../../shared/pipes/role-label.pipe';

@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, MatButtonModule, MatIconModule, DatePipe, RoleLabelPipe],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.scss',
})
export class AdminDashboard {
  protected readonly authService = inject(AuthService);

  protected readonly initials = computed(() => {
    const user = this.authService.currentUser();
    return user
      ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase()
      : '?';
  });
}
