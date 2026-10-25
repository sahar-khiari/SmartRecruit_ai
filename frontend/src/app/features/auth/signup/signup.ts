import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { getApiErrorMessage, getValidationErrors } from '../../../core/utils/http-error.util';
import { passwordStrengthValidator } from '../../../shared/validators/password.validator';
import { passwordsMatchValidator } from '../../../shared/validators/password-match.validator';

@Component({
  selector: 'app-signup',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './signup.html',
  styleUrl: './signup.scss',
})
export class Signup {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly form = this.formBuilder.group(
    {
      firstName: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/\S/)]],
      lastName: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/\S/)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
      password: [
        '',
        [Validators.required, Validators.maxLength(100), passwordStrengthValidator],
      ],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator('password', 'confirmPassword') },
  );

  private readonly passwordValue = toSignal(this.form.controls.password.valueChanges, {
    initialValue: '',
  });

  protected readonly passwordChecks = computed(() => {
    const value = this.passwordValue();
    return {
      length: value.length >= 8,
      upper: /[A-Z]/.test(value),
      lower: /[a-z]/.test(value),
      digit: /\d/.test(value),
      special: /[^A-Za-z0-9]/.test(value),
    };
  });

  protected readonly loading = signal(false);
  protected readonly hidePassword = signal(true);
  protected readonly hideConfirmation = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected togglePasswordVisibility(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  protected toggleConfirmationVisibility(): void {
    this.hideConfirmation.update((hidden) => !hidden);
  }

  protected submit(): void {
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    const { firstName, lastName, email, password } = this.form.getRawValue();

    this.authService
      .signup({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim().toLowerCase(),
        password,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.snackBar.open(
            'Compte créé avec succès. Vous pouvez maintenant vous connecter.',
            'Fermer',
            { duration: 5000, panelClass: 'snackbar-success' },
          );
          this.router.navigate(['/login']);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.handleSignupError(error);
        },
      });
  }

  private handleSignupError(error: unknown): void {
    this.errorMessage.set(
      getApiErrorMessage(error, 'Création du compte impossible. Veuillez réessayer.'),
    );

    const validationErrors = getValidationErrors(error);
    for (const [field, message] of Object.entries(validationErrors)) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ ...(control.errors ?? {}), server: message });
        control.markAsTouched();
      }
    }

    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.form.controls.email.markAsTouched();
    }
  }
}
