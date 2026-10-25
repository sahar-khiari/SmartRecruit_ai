import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ROLE_LABELS, ROLE_OPTIONS, RoleName } from '../../../core/models/role.model';
import { User } from '../../../core/models/user.model';
import { AuthService } from '../../../core/services/auth.service';
import { UserService } from '../../../core/services/user.service';
import { getApiErrorMessage } from '../../../core/utils/http-error.util';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../../shared/components/confirmation-dialog/confirmation-dialog';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { LoadingSpinner } from '../../../shared/components/loading-spinner/loading-spinner';
import { PageHeader } from '../../../shared/components/page-header/page-header';
import { RoleLabelPipe } from '../../../shared/pipes/role-label.pipe';

@Component({
  selector: 'app-user-details',
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    DatePipe,
    PageHeader,
    LoadingSpinner,
    EmptyState,
    RoleLabelPipe,
  ],
  templateUrl: './user-details.html',
  styleUrl: './user-details.scss',
})
export class UserDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly userId = Number(this.route.snapshot.paramMap.get('id'));

  protected readonly user = signal<User | null>(null);
  protected readonly selectedRole = signal<RoleName | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly roleOptions = ROLE_OPTIONS;

  protected readonly currentUserId = computed(
    () => this.authService.currentUser()?.id ?? null,
  );
  protected readonly isSelf = computed(
    () => this.user()?.id != null && this.user()?.id === this.currentUserId(),
  );
  protected readonly fullName = computed(() => {
    const user = this.user();
    return user ? `${user.firstName} ${user.lastName}` : '';
  });
  protected readonly initials = computed(() => {
    const user = this.user();
    return user
      ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase()
      : '?';
  });
  protected readonly roleChanged = computed(() => {
    const user = this.user();
    const role = this.selectedRole();
    return !!user && !!role && user.role !== role;
  });

  constructor() {
    if (!Number.isInteger(this.userId) || this.userId <= 0) {
      this.loading.set(false);
      this.errorMessage.set('Identifiant utilisateur invalide.');
      return;
    }
    this.loadUser();
  }

  protected loadUser(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.userService.getUserById(this.userId).subscribe({
      next: (user) => {
        this.user.set(user);
        this.selectedRole.set(user.role);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          getApiErrorMessage(error, 'Impossible de charger cet utilisateur.'),
        );
      },
    });
  }

  protected applyRole(): void {
    const user = this.user();
    const role = this.selectedRole();

    if (!user || !role || role === user.role || this.isSelf() || this.busy()) {
      return;
    }

    this.openConfirmation(
      {
        title: 'Changer le rôle',
        message: `Attribuer le rôle « ${ROLE_LABELS[role]} » à ${user.firstName} ${user.lastName} ?`,
        confirmLabel: 'Changer le rôle',
      },
      (confirmed) => {
        if (!confirmed) {
          this.selectedRole.set(user.role);
          return;
        }

        this.busy.set(true);
        this.userService.updateRole(user.id, role).subscribe({
          next: (updatedUser) => {
            this.busy.set(false);
            this.user.set(updatedUser);
            this.selectedRole.set(updatedUser.role);
            this.showSuccess('Le rôle de l’utilisateur a été mis à jour.');
          },
          error: () => {
            this.busy.set(false);
            this.selectedRole.set(user.role);
          },
        });
      },
    );
  }

  protected toggleStatus(): void {
    const user = this.user();
    if (!user || this.isSelf() || this.busy()) {
      return;
    }

    if (user.enabled) {
      this.openConfirmation(
        {
          title: 'Désactiver le compte',
          message: `Désactiver le compte de ${user.firstName} ${user.lastName} ? L’utilisateur ne pourra plus se connecter jusqu’à sa réactivation.`,
          confirmLabel: 'Désactiver',
          danger: true,
        },
        (confirmed) => {
          if (confirmed) {
            this.updateStatus(user, false);
          }
        },
      );
      return;
    }

    this.updateStatus(user, true);
  }

  protected deleteUser(): void {
    const user = this.user();
    if (!user || this.isSelf() || this.busy()) {
      return;
    }

    this.openConfirmation(
      {
        title: 'Supprimer l’utilisateur',
        message: `Le compte de ${user.firstName} ${user.lastName} (${user.email}) sera définitivement supprimé. Cette action est irréversible.`,
        confirmLabel: 'Supprimer',
        danger: true,
      },
      (confirmed) => {
        if (!confirmed) {
          return;
        }

        this.busy.set(true);
        this.userService.deleteUser(user.id).subscribe({
          next: (response) => {
            this.busy.set(false);
            this.snackBar.open(
              response.message || 'Utilisateur supprimé avec succès.',
              'Fermer',
              { duration: 4500, panelClass: 'snackbar-success' },
            );
            void this.router.navigate(['/admin/users']);
          },
          error: () => this.busy.set(false),
        });
      },
    );
  }

  private updateStatus(user: User, enabled: boolean): void {
    this.busy.set(true);
    this.userService.updateStatus(user.id, enabled).subscribe({
      next: (updatedUser) => {
        this.busy.set(false);
        this.user.set(updatedUser);
        this.showSuccess(
          enabled ? 'Le compte a été activé.' : 'Le compte a été désactivé.',
        );
      },
      error: () => this.busy.set(false),
    });
  }

  private showSuccess(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 4000,
      panelClass: 'snackbar-success',
    });
  }

  private openConfirmation(
    data: ConfirmationDialogData,
    onClose: (confirmed: boolean) => void,
  ): void {
    this.dialog
      .open<ConfirmationDialog, ConfirmationDialogData, boolean>(ConfirmationDialog, {
        data,
        width: '480px',
        autoFocus: 'dialog',
      })
      .afterClosed()
      .subscribe((confirmed) => onClose(!!confirmed));
  }
}
