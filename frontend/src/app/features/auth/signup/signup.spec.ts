import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FormGroup } from '@angular/forms';
import { provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { Signup } from './signup';
import { AuthService } from '../../../core/services/auth.service';

describe('Signup', () => {
  let fixture: ComponentFixture<Signup>;
  let authMock: { signup: ReturnType<typeof vi.fn> };
  let snackMock: { open: ReturnType<typeof vi.fn> };

  async function setup(): Promise<void> {
    authMock = { signup: vi.fn() };
    snackMock = { open: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Signup],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authMock },
        { provide: MatSnackBar, useValue: snackMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Signup);
    fixture.detectChanges();
  }

  function form(): FormGroup {
    return (fixture.componentInstance as unknown as { form: FormGroup }).form;
  }

  function fillValidForm(): void {
    form().setValue({
      firstName: 'Amina',
      lastName: 'Benali',
      email: 'Amina.Benali@Example.com',
      password: 'Password1!',
      confirmPassword: 'Password1!',
    });
  }

  function submitButton(): HTMLButtonElement {
    return fixture.debugElement.query(By.css('button[type="submit"]'))
      .nativeElement as HTMLButtonElement;
  }

  beforeEach(async () => {
    await setup();
  });

  it('valide les champs obligatoires et n’appelle pas l’API', () => {
    submitButton().click();
    fixture.detectChanges();

    expect(authMock.signup).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('prénom est obligatoire');
    expect(fixture.nativeElement.textContent).toContain('nom est obligatoire');
    expect(fixture.nativeElement.textContent).toContain('adresse email est obligatoire');
  });

  it('refuse un mot de passe ne respectant pas les règles de sécurité', () => {
    form().setValue({
      firstName: 'Amina',
      lastName: 'Benali',
      email: 'amina@example.com',
      password: 'motdepasse',
      confirmPassword: 'motdepasse',
    });
    submitButton().click();
    fixture.detectChanges();

    expect(authMock.signup).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('au moins 8 caractères');
  });

  it('refuse une confirmation différente du mot de passe', () => {
    fillValidForm();
    form().controls['confirmPassword'].setValue('Password2!');
    submitButton().click();
    fixture.detectChanges();

    expect(authMock.signup).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'Les deux mots de passe ne correspondent pas',
    );
  });

  it('crée le compte et redirige vers /login', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fillValidForm();
    authMock.signup.mockReturnValue(of({ message: 'Compte créé avec succès.' }));

    submitButton().click();
    fixture.detectChanges();

    expect(authMock.signup).toHaveBeenCalledWith({
      firstName: 'Amina',
      lastName: 'Benali',
      email: 'amina.benali@example.com',
      password: 'Password1!',
    });
    expect(snackMock.open).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('affiche le message du backend en cas d’email déjà utilisé (409) sans vider le formulaire', () => {
    fillValidForm();
    authMock.signup.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'Un compte existe déjà avec cet email.' },
          }),
      ),
    );

    submitButton().click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Un compte existe déjà avec cet email.',
    );
    expect(form().controls['email'].value).toBe('Amina.Benali@Example.com');
    expect(form().controls['firstName'].value).toBe('Amina');
  });
});
