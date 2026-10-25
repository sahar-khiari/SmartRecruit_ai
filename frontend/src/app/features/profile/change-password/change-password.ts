import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { UserService } from '../../../core/services/user.service';
import { RoleNavigationService } from '../../../core/services/role-navigation.service';
import { getApiErrorMessage } from '../../../core/utils/http-error.util';
import { PageHeader } from '../../../shared/components/page-header/page-header';
import { passwordStrengthValidator } from '../../../shared/validators/password.validator';
import { passwordsMatchValidator } from '../../../shared/validators/password-match.validator';

@Component({
  selector: 'app-change-password',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PageHeader,
  ],
  templateUrl: './change-password.html',
  styleUrl: './change-password.scss',
})
export class ChangePassword {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly userService = inject(UserService);
  private readonly roleNavigation = inject(RoleNavigationService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly profilePath = computed(() =>
    this.roleNavigation.profilePathForCurrentUser('view'),
  );

  protected readonly form = this.formBuilder.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: [
        '',
        [Validators.required, Validators.maxLength(100), passwordStrengthValidator],
      ],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator('newPassword', 'confirmPassword') },
  );

  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hideCurrent = signal(true);
  protected readonly hideNew = signal(true);
  protected readonly hideConfirmation = signal(true);

  protected toggleCurrent(): void {
    this.hideCurrent.update((hidden) => !hidden);
  }

  protected toggleNew(): void {
    this.hideNew.update((hidden) => !hidden);
  }

  protected toggleConfirmation(): void {
    this.hideConfirmation.update((hidden) => !hidden);
  }

  protected submit(): void {
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.form.getRawValue();
    this.saving.set(true);

    this.userService.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.saving.set(false);
        this.snackBar.open('Votre mot de passe a été modifié avec succès.', 'Fermer', {
          duration: 5000,
          panelClass: 'snackbar-success',
        });
        void this.router.navigateByUrl(this.profilePath());
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(
          error instanceof HttpErrorResponse && error.status === 401
            ? 'Le mot de passe actuel est incorrect.'
            : getApiErrorMessage(
                error,
                'Le changement de mot de passe a échoué. Veuillez réessayer.',
              ),
        );
      },
    });
  }
}
