import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { RoleNavigationService } from '../../../core/services/role-navigation.service';
import { getApiErrorMessage } from '../../../core/utils/http-error.util';

@Component({
  selector: 'app-login',
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
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly authService = inject(AuthService);
  private readonly roleNavigation = inject(RoleNavigationService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly form = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    password: ['', [Validators.required]],
  });

  protected readonly loading = signal(false);
  protected readonly hidePassword = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected togglePasswordVisibility(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  protected submit(): void {
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.authService.login(this.form.getRawValue()).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.snackBar.open(
          `Bienvenue, ${response.user.firstName} !`,
          'Fermer',
          { duration: 4000, panelClass: 'snackbar-success' },
        );
        this.router.navigateByUrl(this.destinationUrl());
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.loginErrorMessage(error));
      },
    });
  }

  private destinationUrl(): string {
    const returnUrl = this.router.parseUrl(this.router.url).queryParams['returnUrl'] as
      | string
      | undefined;

    if (!returnUrl) {
      return this.roleNavigation.homePathForCurrentUser();
    }

    return returnUrl.startsWith('/') && !returnUrl.startsWith('//')
      ? returnUrl
      : this.roleNavigation.homePathForCurrentUser();
  }

  private loginErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Email ou mot de passe incorrect.';
    }
    return getApiErrorMessage(error, 'Connexion impossible. Veuillez réessayer.');
  }
}
