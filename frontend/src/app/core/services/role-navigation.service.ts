import { computed, inject, Injectable } from '@angular/core';
import { RoleName } from '../models/role.model';
import { AuthService } from './auth.service';

export type ProfileSection = 'view' | 'edit' | 'change-password';

/**
 * Centralise les chemins d'accueil et de profil selon le rôle.
 * ADMIN = back-office (/admin), CANDIDATE et RECRUITER = front-office (/app).
 * Aucun composant ne doit recopier cette logique de redirection.
 */
@Injectable({ providedIn: 'root' })
export class RoleNavigationService {
  private readonly authService = inject(AuthService);

  private readonly homePaths: Record<RoleName, string> = {
    ADMIN: '/admin',
    RECRUITER: '/app/recruiter',
    CANDIDATE: '/app/candidate',
  };

  private readonly profilePaths: Record<RoleName, string> = {
    ADMIN: '/admin/profile',
    RECRUITER: '/app/profile',
    CANDIDATE: '/app/profile',
  };

  readonly currentRole = computed<RoleName | null>(
    () => this.authService.currentUser()?.role ?? null,
  );

  homePath(role: RoleName): string {
    return this.homePaths[role];
  }

  profilePath(role: RoleName, section: ProfileSection = 'view'): string {
    const basePath = this.profilePaths[role];
    return section === 'view' ? basePath : `${basePath}/${section}`;
  }

  /** Page d'accueil du rôle connecté, ou /login si la session est absente. */
  homePathForCurrentUser(): string {
    const role = this.currentRole();
    return role ? this.homePath(role) : '/login';
  }

  /** Profil du rôle connecté, ou /login si la session est absente. */
  profilePathForCurrentUser(section: ProfileSection = 'view'): string {
    const role = this.currentRole();
    return role ? this.profilePath(role, section) : '/login';
  }

  isAdminSpace(): boolean {
    return this.currentRole() === 'ADMIN';
  }
}
