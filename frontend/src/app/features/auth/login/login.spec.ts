import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FormGroup } from '@angular/forms';
import { provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { signal, WritableSignal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { Login } from './login';
import { AuthService } from '../../../core/services/auth.service';
import { User } from '../../../core/models/user.model';
import { TEST_ADMIN, TEST_USER } from '../../../../testing/test-utils';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let authMock: {
    login: ReturnType<typeof vi.fn>;
    currentUser: () => User | null;
  };
  let snackMock: { open: ReturnType<typeof vi.fn> };
  let currentUser: WritableSignal<User | null>;

  async function setup(): Promise<void> {
    currentUser = signal<User | null>(null);
    authMock = {
      login: vi.fn(),
      currentUser: () => currentUser(),
    };
    snackMock = { open: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authMock },
        { provide: MatSnackBar, useValue: snackMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
  }

  function form(): FormGroup {
    return (fixture.componentInstance as unknown as { form: FormGroup }).form;
  }

  function submitButton(): HTMLButtonElement {
    return fixture.debugElement.query(By.css('button[type="submit"]'))
      .nativeElement as HTMLButtonElement;
  }

  function loginAs(user: User): void {
    authMock.login.mockImplementation(() => {
      currentUser.set(user);
      return of({
        accessToken: 'token',
        tokenType: 'Bearer',
        expiresIn: 3600,
        user,
      });
    });
  }

  beforeEach(async () => {
    await setup();
  });

  it('affiche les erreurs et n’appelle pas l’API si le formulaire est vide', () => {
    submitButton().click();
    fixture.detectChanges();

    expect(authMock.login).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('adresse email est obligatoire');
    expect(fixture.nativeElement.textContent).toContain('mot de passe est obligatoire');
  });

  it('refuse un email au format invalide', () => {
    form().setValue({ email: 'email-invalide', password: 'Password1!' });
    submitButton().click();
    fixture.detectChanges();

    expect(authMock.login).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('format de l’adresse email est invalide');
  });

  it('affiche « Email ou mot de passe incorrect » sur un 401 sans redirection', () => {
    form().setValue({ email: 'candidate@example.com', password: 'Password1!' });
    authMock.login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: { message: 'Email ou mot de passe incorrect' },
          }),
      ),
    );

    submitButton().click();
    fixture.detectChanges();

    expect(authMock.login).toHaveBeenCalledWith({
      email: 'candidate@example.com',
      password: 'Password1!',
    });
    expect(fixture.nativeElement.textContent).toContain('Email ou mot de passe incorrect');
  });

  it('redirige un CANDIDATE vers /app/candidate après connexion', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    loginAs(TEST_USER);
    form().setValue({ email: TEST_USER.email, password: 'Password1!' });

    submitButton().click();
    fixture.detectChanges();

    expect(snackMock.open).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith('/app/candidate');
  });

  it('redirige un RECRUITER vers /app/recruiter après connexion', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const recruiter: User = { ...TEST_USER, id: 7, role: 'RECRUITER' };
    loginAs(recruiter);
    form().setValue({ email: recruiter.email, password: 'Password1!' });

    submitButton().click();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith('/app/recruiter');
  });

  it('redirige un ADMIN vers /admin après connexion', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    loginAs(TEST_ADMIN);
    form().setValue({ email: TEST_ADMIN.email, password: 'Password1!' });

    submitButton().click();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith('/admin');
  });
});
