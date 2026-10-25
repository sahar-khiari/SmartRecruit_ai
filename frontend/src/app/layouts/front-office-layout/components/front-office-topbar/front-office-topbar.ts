import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../../../core/services/auth.service';
import { RoleNavigationService } from '../../../../core/services/role-navigation.service';
import { RoleLabelPipe } from '../../../../shared/pipes/role-label.pipe';
import { UserMenu } from '../../../../shared/components/user-menu/user-menu';

interface FrontOfficeNavItem {
  icon: string;
  label: string;
  path?: string;
  soon?: boolean;
}

/**
 * Topbar horizontale du front-office.
 * Les modules métier non développés sont affichés comme « Bientôt »,
 * sans route inexistante.
 */
@Component({
  selector: 'app-front-office-topbar',
  imports: [
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
    RoleLabelPipe,
    UserMenu,
  ],
  templateUrl: './front-office-topbar.html',
  styleUrl: './front-office-topbar.scss',
})
export class FrontOfficeTopbar {
  protected readonly authService = inject(AuthService);
  private readonly roleNavigation = inject(RoleNavigationService);

  protected readonly homePath = computed(() =>
    this.roleNavigation.homePathForCurrentUser(),
  );

  protected readonly navItems = computed<readonly FrontOfficeNavItem[]>(() => {
    if (this.roleNavigation.currentRole() === 'RECRUITER') {
      return [
        { icon: 'home', label: 'Accueil', path: '/app/recruiter' },
        { icon: 'work_outline', label: 'Mes offres', path: '/app/recruteur/offres' },
        { icon: 'groups', label: 'Candidats', path: '/app/candidats' },
        { icon: 'inbox', label: 'Candidatures', soon: true },
        { icon: 'video_call', label: 'Entretiens', soon: true },
        { icon: 'smart_toy', label: 'Assistant RH', path: '/app/assistant-rh' },
        { icon: 'quiz', label: 'Tests techniques', path: '/app/tests-techniques' },
        { icon: 'person', label: 'Mon profil', path: '/app/profile' },
      ];
    }

    return [
      { icon: 'home', label: 'Accueil', path: '/app/candidate' },
      { icon: 'description', label: 'Mon CV', path: '/app/candidate/cv' },
      { icon: 'work_outline', label: 'Offres', path: '/app/offres' },
      { icon: 'recommend', label: 'Recommandations IA', path: '/app/matching/recommandations' },
      { icon: 'badge', label: 'Mon CV & Matching', path: '/app/matching/profile' },
      { icon: 'fact_check', label: 'Mes candidatures', soon: true },
      { icon: 'video_call', label: 'Mes entretiens', soon: true },
      { icon: 'quiz', label: 'Tests techniques', path: '/app/tests-techniques' },
      { icon: 'smart_toy', label: 'Assistant RH', path: '/app/assistant-rh' },
      { icon: 'person', label: 'Mon profil', path: '/app/profile' },
    ];
  });

  protected logout(): void {
    this.authService.logout();
  }
}
