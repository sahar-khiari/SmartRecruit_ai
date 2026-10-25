import { Component, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import { MatSidenavModule } from '@angular/material/sidenav';
import { AdminSidebar } from './components/admin-sidebar/admin-sidebar';
import { AdminTopbar } from './components/admin-topbar/admin-topbar';

/**
 * Back-office réservé au rôle ADMIN.
 * CANDIDATE et RECRUITER utilisent FrontOfficeLayout.
 */
@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, MatSidenavModule, AdminSidebar, AdminTopbar],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
})
export class AdminLayout {
  private readonly router = inject(Router);
  private readonly breakpoints = inject(BreakpointObserver);

  protected readonly isMobile = toSignal(
    this.breakpoints.observe('(max-width: 959.98px)').pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  private readonly isTablet = toSignal(
    this.breakpoints
      .observe('(min-width: 960px) and (max-width: 1279.98px)')
      .pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  protected readonly sideCollapsed = signal(false);
  protected readonly mobileOpen = signal(false);

  protected readonly sidenavMode = computed<'over' | 'side'>(() =>
    this.isMobile() ? 'over' : 'side',
  );

  protected readonly sidenavOpened = computed(() =>
    this.isMobile() ? this.mobileOpen() : true,
  );

  protected readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.resolveTitle(this.router.url)),
      startWith(this.resolveTitle(this.router.url)),
    ),
    { initialValue: 'Tableau de bord administratif' },
  );

  constructor() {
    effect(() => {
      if (this.isTablet()) {
        this.sideCollapsed.set(true);
      }
    });
  }

  protected toggleSidenav(): void {
    if (this.isMobile()) {
      this.mobileOpen.update((opened) => !opened);
      return;
    }
    this.sideCollapsed.update((collapsed) => !collapsed);
  }

  protected onNavigated(): void {
    if (this.isMobile()) {
      this.mobileOpen.set(false);
    }
  }

  private resolveTitle(url: string): string {
    const path = url.split('?')[0];

    if (path.startsWith('/admin/users/')) {
      return 'Détails de l’utilisateur';
    }

    if (path === '/admin/knowledge-base/new') {
      return 'Ajouter une ressource RH';
    }

    if (/^\/admin\/knowledge-base\/\d+\/edit$/.test(path)) {
      return 'Modifier une ressource RH';
    }

    if (/^\/admin\/knowledge-base\/\d+$/.test(path)) {
      return 'Détail de la ressource RH';
    }

    const titles: Record<string, string> = {
      '/admin': 'Tableau de bord administratif',
      '/admin/dashboard': 'Tableau de bord administratif',
      '/admin/users': 'Gestion des utilisateurs',
      '/admin/knowledge-base': 'Base de connaissances RH',
      '/admin/profile': 'Mon profil',
      '/admin/profile/edit': 'Modifier mon profil',
      '/admin/profile/change-password': 'Changer le mot de passe',
    };

    return titles[path] ?? 'Back-office SmartRecruit AI';
  }
}
