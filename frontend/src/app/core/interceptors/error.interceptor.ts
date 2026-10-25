import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_ENDPOINTS } from '../constants/api-endpoints.constants';
import { AuthStateService } from '../services/auth-state.service';
import { TokenStorageService } from '../services/token-storage.service';
import { getApiErrorMessage } from '../utils/http-error.util';
import {
  HANDLE_UNAUTHORIZED_INLINE,
  SUPPRESS_ERROR_NOTIFICATION,
} from './error-notification.context';

let lastSessionExpiryNotification = 0;

/**
 * Gestion centralisée des erreurs HTTP + notifications MatSnackBar.
 * Les endpoints login/signup sont gérés par leurs formulaires (messages inline),
 * ce qui évite les notifications en double et les boucles de redirection.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const snackBar = inject(MatSnackBar);
  const tokenStorage = inject(TokenStorageService);
  const authState = inject(AuthStateService);
  const router = inject(Router);

  const isAuthFormRequest =
    request.url.endsWith(API_ENDPOINTS.auth.login) ||
    request.url.endsWith(API_ENDPOINTS.auth.signup);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      if (isAuthFormRequest) {
        return throwError(() => error);
      }

      if (
        error.status === 401 &&
        request.context.get(HANDLE_UNAUTHORIZED_INLINE)
      ) {
        return throwError(() => error);
      }

      if (error.status === 401) {
        tokenStorage.clearSession();
        authState.clear();

        const now = Date.now();
        if (now - lastSessionExpiryNotification > 3_000) {
          lastSessionExpiryNotification = now;
          snackBar.open('Votre session a expiré. Veuillez vous reconnecter.', 'Fermer', {
            duration: 6000,
            panelClass: 'snackbar-error',
          });
        }

        const returnUrl = router.url.startsWith('/login') ? undefined : router.url;
        void router.navigate(['/login'], {
          queryParams: returnUrl ? { returnUrl } : {},
        });
        return throwError(() => error);
      }

      if (request.context.get(SUPPRESS_ERROR_NOTIFICATION)) {
        return throwError(() => error);
      }

      if (error.status === 403) {
        snackBar.open(
          'Accès refusé : vous ne disposez pas des droits nécessaires.',
          'Fermer',
          { duration: 5000, panelClass: 'snackbar-error' },
        );
        return throwError(() => error);
      }

      const message = getApiErrorMessage(error);
      snackBar.open(message, 'Fermer', {
        duration: 5000,
        panelClass: 'snackbar-error',
      });

      return throwError(() => error);
    }),
  );
};
