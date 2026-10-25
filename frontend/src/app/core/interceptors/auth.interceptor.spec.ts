import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../environments/environment';
import { createJwt, TEST_USER } from '../../../testing/test-utils';
import { authInterceptor } from './auth.interceptor';
import { AuthStateService } from '../services/auth-state.service';
import { TokenStorageService } from '../services/token-storage.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let storage: TokenStorageService;
  let authState: AuthStateService;
  let snackMock: { open: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    snackMock = { open: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackMock },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorageService);
    authState = TestBed.inject(AuthStateService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('ajoute le header Authorization aux appels vers l’API', () => {
    const token = createJwt(3600);
    storage.saveToken(token);

    http.get(`${environment.apiUrl}/users/me`).subscribe();
    const request = httpMock.expectOne(`${environment.apiUrl}/users/me`);

    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    request.flush(TEST_USER);
  });

  it('n’ajoute pas le header aux requêtes externes', () => {
    storage.saveToken(createJwt(3600));

    http.get('https://api.example.com/donnees').subscribe();
    const request = httpMock.expectOne('https://api.example.com/donnees');

    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('laisse la requête inchangée sans token', () => {
    http.get(`${environment.apiUrl}/users/me`).subscribe();
    const request = httpMock.expectOne(`${environment.apiUrl}/users/me`);

    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(TEST_USER);
  });

  it('nettoie la session et redirige vers /login si le token est expiré', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    storage.saveToken(createJwt(-60));
    storage.saveUser(TEST_USER);
    authState.setUser(TEST_USER);

    let capturedError: HttpErrorResponse | undefined;
    http.get(`${environment.apiUrl}/users/me`).subscribe({
      error: (error: HttpErrorResponse) => {
        capturedError = error;
      },
    });

    httpMock.expectNone(`${environment.apiUrl}/users/me`);
    expect(capturedError?.status).toBe(401);
    expect(storage.getToken()).toBeNull();
    expect(storage.getUser()).toBeNull();
    expect(authState.user()).toBeNull();
    expect(snackMock.open).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(
      ['/login'],
      expect.objectContaining({ queryParams: expect.any(Object) }),
    );
  });
});
