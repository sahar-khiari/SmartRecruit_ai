import { HttpContextToken } from '@angular/common/http';

/**
 * Permet à un composant de gérer lui-même l'affichage d'une erreur
 * (message inline) sans que l'intercepteur global n'affiche aussi un snackbar.
 * Les erreurs 401 de session restent toujours traitées globalement.
 */
export const SUPPRESS_ERROR_NOTIFICATION = new HttpContextToken<boolean>(() => false);

/**
 * Le backend renvoie 401 lorsque le mot de passe actuel est incorrect sur
 * PUT /api/users/me/password. Ce 401 ne signifie pas que le token JWT est expiré :
 * il doit être affiché dans le formulaire sans détruire la session.
 */
export const HANDLE_UNAUTHORIZED_INLINE = new HttpContextToken<boolean>(() => false);
