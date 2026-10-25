import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { createJwt, TEST_USER } from '../../../testing/test-utils';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let storage: TokenStorageService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    storage = TestBed.inject(TokenStorageService);
    router = TestBed.inject(Router);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('connecte l’utilisateur et stocke la session', () => {
    service = TestBed.inject(AuthService);

    service
      .login({ email: 'amina.benali@example.com', password: 'Password1!' })
      .subscribe();

    const request = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'amina.benali@example.com',
      password: 'Password1!',
    });

    request.flush({
      accessToken: createJwt(3600),
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: TEST_USER,
    });

    expect(service.currentUser()).toEqual(TEST_USER);
    expect(service.isAuthenticated()).toBe(true);
    expect(storage.getToken()).not.toBeNull();
    expect(storage.getUser()).toEqual(TEST_USER);
  });

  it('restaure une session valide après actualisation', () => {
    storage.saveToken(createJwt(3600));
    storage.saveUser(TEST_USER);

    service = TestBed.inject(AuthService);

    expect(service.currentUser()).toEqual(TEST_USER);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('efface une session expirée', () => {
    storage.saveToken(createJwt(-120));
    storage.saveUser(TEST_USER);

    service = TestBed.inject(AuthService);

    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(storage.getToken()).toBeNull();
    expect(storage.getUser()).toBeNull();
  });

  it('vide la session à la déconnexion et redirige vers /login', () => {
    service = TestBed.inject(AuthService);
    storage.saveToken(createJwt(3600));
    service.updateCurrentUser(TEST_USER);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    service.logout();

    expect(storage.getToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('vérifie les rôles à partir de l’utilisateur courant', () => {
    service = TestBed.inject(AuthService);
    service.updateCurrentUser(TEST_USER);

    expect(service.hasRole('CANDIDATE')).toBe(true);
    expect(service.hasRole(['RECRUITER', 'CANDIDATE'])).toBe(true);
    expect(service.hasRole('ADMIN')).toBe(false);
  });
});
