import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { RoleNavigationService } from '../services/role-navigation.service';

/**
 * Empêche un utilisateur authentifié d'accéder à /login et /signup.
 * Il est renvoyé vers l'accueil correspondant à son rôle.
 */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const roleNavigation = inject(RoleNavigationService);

  if (!authService.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree([roleNavigation.homePathForCurrentUser()]);
};
