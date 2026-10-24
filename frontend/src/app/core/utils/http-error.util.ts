import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/api-error.model';

const SERVER_UNAVAILABLE =
  'Le serveur est indisponible. Vérifiez que le backend Spring Boot est démarré sur le port 8080.';

/**
 * Extrait un message lisible depuis une erreur HTTP du backend.
 */
export function getApiErrorMessage(error: unknown, fallback = 'Une erreur est survenue.'): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }

  if (error.status === 0) {
    return SERVER_UNAVAILABLE;
  }

  const body = error.error as ApiError | string | null;
  if (body && typeof body === 'object') {
    if (body.validationErrors) {
      const firstValidationMessage = Object.values(body.validationErrors)[0];
      if (firstValidationMessage) {
        return firstValidationMessage;
      }
    }
    if (body.message) {
      return body.message;
    }
  }

  switch (error.status) {
    case 401:
      return 'Email ou mot de passe incorrect.';
    case 403:
      return 'Accès refusé : vous ne disposez pas des droits nécessaires.';
    case 404:
      return 'Ressource introuvable.';
    case 409:
      return 'Conflit : cette valeur est déjà utilisée.';
    case 500:
      return 'Erreur interne du serveur. Veuillez réessayer plus tard.';
    default:
      return fallback;
  }
}

export function getValidationErrors(error: unknown): Record<string, string> {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ApiError | null;
    if (body && typeof body === 'object' && body.validationErrors) {
      return body.validationErrors;
    }
  }
  return {};
}

export function isServerUnavailable(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 0;
}
