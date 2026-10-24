import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { RoleName } from '../models/role.model';
import { AuthService } from '../services/auth.service';
import { RoleNavigationService } from '../services/role-navigation.service';

/**
 * Vérifie les rôles autorisés déclarés dans `route.data.roles`.
 * La sécurité finale reste assurée par Spring Security côté backend.
 */
export const roleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const roleNavigation = inject(RoleNavigationService);

  const requiredRoles = (route.data['roles'] as RoleName[] | undefined) ?? [];

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login'], {
      queryParams: { returnUrl: route.pathFromRoot.map((r) => r.url.join('/')).join('/') },
    });
  }

  if (requiredRoles.length === 0 || authService.hasRole(requiredRoles)) {
    return true;
  }

  const role = authService.currentUser()?.role;

  // Un ADMIN n'a pas sa place dans le front-office : retour au back-office.
  if (role === 'ADMIN' && !requiredRoles.includes('ADMIN')) {
    return router.createUrlTree([roleNavigation.homePath(role)]);
  }

  return router.createUrlTree(['/access-denied']);
};
