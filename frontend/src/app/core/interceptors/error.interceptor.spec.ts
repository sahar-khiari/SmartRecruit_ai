import { TestBed } from '@angular/core/testing';
import {
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
import { errorInterceptor } from './error.interceptor';
import { UserService } from '../services/user.service';
import { AuthStateService } from '../services/auth-state.service';
import { TokenStorageService } from '../services/token-storage.service';

describe('errorInterceptor', () => {
  let userService: UserService;
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
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackMock },
      ],
    });

    userService = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorageService);
    authState = TestBed.inject(AuthStateService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('conserve la session quand le mot de passe actuel est refusé (401 métier)', () => {
    storage.saveToken(createJwt(3600));
    storage.saveUser(TEST_USER);
    authState.setUser(TEST_USER);
    const navigateSpy = vi
      .spyOn(TestBed.inject(Router), 'navigate')
      .mockResolvedValue(true);

    let capturedError: HttpErrorResponse | undefined;
    userService
      .changePassword({ currentPassword: 'incorrect', newPassword: 'NouveauPass1!' })
      .subscribe({
        error: (error: HttpErrorResponse) => {
          capturedError = error;
        },
      });

    const request = httpMock.expectOne(
      `${environment.apiUrl}/users/me/password`,
    );
    request.flush(
      { message: 'Email ou mot de passe incorrect' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(capturedError?.status).toBe(401);
    expect(storage.getToken()).not.toBeNull();
    expect(storage.getUser()).toEqual(TEST_USER);
    expect(authState.user()).toEqual(TEST_USER);
    expect(snackMock.open).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
