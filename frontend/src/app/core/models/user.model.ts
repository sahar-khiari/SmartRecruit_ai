import { RoleName } from './role.model';

/**
 * Utilisateur exposé par l'API. Le backend ne renvoie jamais de mot de passe.
 */
export interface User {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: RoleName;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  email: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface UpdateUserStatusRequest {
  enabled: boolean;
}

export interface UpdateUserRoleRequest {
  role: RoleName;
}
