import { Injectable } from '@angular/core';
import { JwtPayload } from '../models/auth.model';
import { User } from '../models/user.model';

const TOKEN_KEY = 'smartrecruit.access_token';
const USER_KEY = 'smartrecruit.current_user';
const EXPIRY_SKEW_MS = 5_000;

/**
 * Gestion du JWT et des données utilisateur non sensibles dans localStorage.
 *
 * Sécurité : localStorage est vulnérable au XSS. En production, préférer un cookie
 * HttpOnly + Secure + SameSite, avec protection CSRF côté backend.
 * Aucun mot de passe, hash ou secret n'est stocké ici.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  saveToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  hasToken(): boolean {
    return !!this.getToken();
  }

  saveUser(user: User): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  getUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      const user = JSON.parse(raw) as User;
      if (!user || typeof user.id !== 'number' || !user.email || !user.role) {
        return null;
      }
      return user;
    } catch {
      return null;
    }
  }

  clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  getTokenExpirationDate(token: string | null = this.getToken()): Date | null {
    const payload = this.decodePayload(token);
    if (!payload?.exp) {
      return null;
    }
    return new Date(payload.exp * 1000);
  }

  /**
   * Un token absent, malformé ou expiré (avec marge de 5 s) est considéré invalide.
   */
  isTokenExpired(token: string | null = this.getToken()): boolean {
    if (!token) {
      return true;
    }
    const payload = this.decodePayload(token);
    if (!payload?.exp) {
      return true;
    }
    return payload.exp * 1000 <= Date.now() + EXPIRY_SKEW_MS;
  }

  private decodePayload(token: string | null): JwtPayload | null {
    if (!token) {
      return null;
    }
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }
    try {
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
      const binary = atob(padded);
      const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
      return JSON.parse(new TextDecoder().decode(bytes)) as JwtPayload;
    } catch {
      return null;
    }
  }
}
