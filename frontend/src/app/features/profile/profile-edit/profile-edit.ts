import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { User } from '../../../core/models/user.model';
import { AuthService } from '../../../core/services/auth.service';
import { RoleNavigationService } from '../../../core/services/role-navigation.service';
import { UserService } from '../../../core/services/user.service';
import { getApiErrorMessage, getValidationErrors } from '../../../core/utils/http-error.util';
import { PageHeader } from '../../../shared/components/page-header/page-header';

@Component({
  selector: 'app-profile-edit',
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
  templateUrl: './profile-edit.html',
  styleUrl: './profile-edit.scss',
})
export class ProfileEdit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly roleNavigation = inject(RoleNavigationService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly profilePath = computed(() =>
    this.roleNavigation.profilePathForCurrentUser('view'),
  );

  protected readonly form = this.formBuilder.group({
    firstName: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/\S/)]],
    lastName: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/\S/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
  });

  protected readonly saving = signal(false);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    const user = this.authService.currentUser();
    if (user) {
      this.fillForm(user);
      return;
    }

    this.loading.set(true);
    this.authService.getCurrentUser(true).subscribe({
      next: (currentUser) => {
        this.fillForm(currentUser);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          getApiErrorMessage(error, 'Impossible de charger votre profil.'),
        );
      },
    });
  }

  protected submit(): void {
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { firstName, lastName, email } = this.form.getRawValue();
    this.saving.set(true);

    this.userService
      .updateProfile({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim().toLowerCase(),
      })
      .subscribe({
        next: (updatedUser) => {
          this.saving.set(false);
          this.authService.updateCurrentUser(updatedUser);
          this.snackBar.open('Votre profil a été mis à jour.', 'Fermer', {
            duration: 4000,
            panelClass: 'snackbar-success',
          });
          void this.router.navigateByUrl(this.profilePath());
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.handleError(error);
        },
      });
  }

  private fillForm(user: User): void {
    this.form.patchValue({
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
    });
  }

  private handleError(error: unknown): void {
    const message = getApiErrorMessage(
      error,
      'La modification du profil a échoué. Veuillez réessayer.',
    );

    if (error instanceof HttpErrorResponse && error.status === 409) {
      const emailControl = this.form.controls.email;
      emailControl.setErrors({ ...(emailControl.errors ?? {}), server: message });
      emailControl.markAsTouched();
      return;
    }

    this.errorMessage.set(message);

    const validationErrors = getValidationErrors(error);
    for (const [field, fieldMessage] of Object.entries(validationErrors)) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ ...(control.errors ?? {}), server: fieldMessage });
        control.markAsTouched();
      }
    }
  }
}
