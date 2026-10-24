import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../constants/api-endpoints.constants';
import { AuthStateService } from '../services/auth-state.service';
import { TokenStorageService } from '../services/token-storage.service';

/**
 * Ajoute `Authorization: Bearer <token>` uniquement aux appels vers l'API SmartRecruit AI.
 * Les endpoints publics d'authentification ne reçoivent jamais de token.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const tokenStorage = inject(TokenStorageService);
  const authState = inject(AuthStateService);
  const router = inject(Router);
  const snackBar = inject(MatSnackBar);

  if (!request.url.startsWith(environment.apiUrl)) {
    return next(request);
  }

  const isPublicAuthEndpoint =
    request.url.endsWith(API_ENDPOINTS.auth.login) ||
    request.url.endsWith(API_ENDPOINTS.auth.signup);

  if (isPublicAuthEndpoint) {
    return next(request);
  }

  const token = tokenStorage.getToken();
  if (!token) {
    return next(request);
  }

  if (tokenStorage.isTokenExpired(token)) {
    tokenStorage.clearSession();
    authState.clear();
    snackBar.open('Votre session a expiré. Veuillez vous reconnecter.', 'Fermer', {
      duration: 6000,
      panelClass: 'snackbar-error',
    });
    const returnUrl = router.url.startsWith('/login') ? undefined : router.url;
    void router.navigate(['/login'], {
      queryParams: returnUrl ? { returnUrl } : {},
    });
    return throwError(
      () => new HttpErrorResponse({ status: 401, statusText: 'Session expirée' }),
    );
  }

  return next(
    request.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};
