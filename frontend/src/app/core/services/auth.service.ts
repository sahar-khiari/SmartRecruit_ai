import { computed, inject, Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../constants/api-endpoints.constants';
import { SUPPRESS_ERROR_NOTIFICATION } from '../interceptors/error-notification.context';
import { AuthResponse, LoginRequest, MessageResponse, SignupRequest } from '../models/auth.model';
import { RoleName } from '../models/role.model';
import { User } from '../models/user.model';
import { AuthStateService } from './auth-state.service';
import { TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly authState = inject(AuthStateService);

  readonly currentUser = this.authState.user;
  readonly isLoggedIn = this.authState.isAuthenticated;
  readonly displayName = computed(() => {
    const user = this.currentUser();
    return user ? `${user.firstName} ${user.lastName}` : '';
  });

  constructor() {
    this.restoreSession();
  }

  signup(request: SignupRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.auth.signup}`,
      request,
    );
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}${API_ENDPOINTS.auth.login}`, request)
      .pipe(tap((response) => this.startSession(response)));
  }

  /**
   * Déconnexion locale : le backend ne gère pas de blacklist JWT.
   * La suppression du token côté client est la déconnexion effective.
   */
  logout(): void {
    this.tokenStorage.clearSession();
    this.authState.clear();
    void this.router.navigate(['/login']);
  }

  getCurrentUser(silent = false): Observable<User> {
    const options = silent
      ? { context: new HttpContext().set(SUPPRESS_ERROR_NOTIFICATION, true) }
      : {};
    return this.http
      .get<User>(`${environment.apiUrl}${API_ENDPOINTS.users.me}`, options)
      .pipe(tap((user) => this.updateCurrentUser(user)));
  }

  updateCurrentUser(user: User): void {
    this.tokenStorage.saveUser(user);
    this.authState.setUser(user);
  }

  isAuthenticated(): boolean {
    const token = this.tokenStorage.getToken();
    if (!token || this.tokenStorage.isTokenExpired(token) || !this.currentUser()) {
      if (token) {
        this.tokenStorage.clearSession();
        this.authState.clear();
      }
      return false;
    }
    return true;
  }

  hasRole(roles: RoleName | readonly RoleName[]): boolean {
    const user = this.currentUser();
    if (!user) {
      return false;
    }
    const required = Array.isArray(roles) ? roles : [roles];
    return required.includes(user.role);
  }

  private startSession(response: AuthResponse): void {
    this.tokenStorage.saveToken(response.accessToken);
    this.updateCurrentUser(response.user);
  }

  private restoreSession(): void {
    const token = this.tokenStorage.getToken();
    const user = this.tokenStorage.getUser();

    if (token && user && !this.tokenStorage.isTokenExpired(token)) {
      this.authState.setUser(user);
      return;
    }

    if (token || user) {
      this.tokenStorage.clearSession();
    }
  }
}
