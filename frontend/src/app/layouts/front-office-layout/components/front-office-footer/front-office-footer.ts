import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { RoleNavigationService } from '../../../../core/services/role-navigation.service';

@Component({
  selector: 'app-front-office-footer',
  imports: [RouterLink, MatIconModule],
  templateUrl: './front-office-footer.html',
  styleUrl: './front-office-footer.scss',
})
export class FrontOfficeFooter {
  private readonly roleNavigation = inject(RoleNavigationService);

  protected readonly currentYear = new Date().getFullYear();
  protected readonly homePath = computed(() =>
    this.roleNavigation.homePathForCurrentUser(),
  );
  protected readonly profilePath = computed(() =>
    this.roleNavigation.profilePathForCurrentUser('view'),
  );
}
