import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelect, MatSelectChange, MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { environment } from '../../../../environments/environment';
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
  selector: 'app-user-list',
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    MatTooltipModule,
    DatePipe,
    PageHeader,
    LoadingSpinner,
    EmptyState,
    RoleLabelPipe,
  ],
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss',
})
export class UserList {
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly totalElements = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal<number>(environment.defaultPageSize);
  protected readonly sortActive = signal('id');
  protected readonly sortDirection = signal<'asc' | 'desc'>('asc');
  protected readonly busyUserId = signal<number | null>(null);

  protected readonly displayedColumns: string[] = [
    'id',
    'firstName',
    'lastName',
    'email',
    'role',
    'enabled',
    'createdAt',
    'actions',
  ];

  protected readonly pageSizeOptions: number[] = [...environment.pageSizeOptions];
  protected readonly roleOptions = ROLE_OPTIONS;
  protected readonly currentUserId = computed(
    () => this.authService.currentUser()?.id ?? null,
  );

  constructor() {
    this.loadUsers();
  }

  protected loadUsers(page = this.pageIndex(), size = this.pageSize()): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.userService
      .getUsers(page, size, `${this.sortActive()},${this.sortDirection()}`)
      .subscribe({
        next: (response) => {
          this.users.set(response.content);
          this.totalElements.set(response.totalElements);
          this.pageIndex.set(response.page);
          this.pageSize.set(response.size);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(
            getApiErrorMessage(error, 'Impossible de charger la liste des utilisateurs.'),
          );
        },
      });
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadUsers(event.pageIndex, event.pageSize);
  }

  protected onSort(sort: Sort): void {
    this.sortActive.set(sort.active || 'id');
    this.sortDirection.set(sort.direction === 'desc' ? 'desc' : 'asc');
    this.pageIndex.set(0);
    this.loadUsers(0, this.pageSize());
  }

  protected isSelf(user: User): boolean {
    return user.id === this.currentUserId();
  }

  protected changeRole(user: User, change: MatSelectChange, select: MatSelect): void {
    const role = change.value as RoleName;

    if (role === user.role || this.isSelf(user)) {
      select.writeValue(user.role);
      return;
    }

    this.openConfirmation(
      {
        title: 'Changer le rôle',
        message: `Attribuer le rôle « ${ROLE_LABELS[role]} » à ${user.firstName} ${user.lastName} ?`,
        confirmLabel: 'Changer le rôle',
      },
      () => {
        this.busyUserId.set(user.id);
        this.userService.updateRole(user.id, role).subscribe({
          next: (updatedUser) => {
            this.busyUserId.set(null);
            this.replaceUser(updatedUser);
            this.showSuccess('Le rôle de l’utilisateur a été mis à jour.');
          },
          error: () => {
            this.busyUserId.set(null);
            select.writeValue(user.role);
          },
        });
      },
      () => select.writeValue(user.role),
    );
  }

  protected toggleStatus(user: User): void {
    if (this.isSelf(user) || this.busyUserId() !== null) {
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
        () => this.setStatus(user, false),
      );
      return;
    }

    this.setStatus(user, true);
  }

  protected deleteUser(user: User): void {
    if (this.isSelf(user) || this.busyUserId() !== null) {
      return;
    }

    this.openConfirmation(
      {
        title: 'Supprimer l’utilisateur',
        message: `Le compte de ${user.firstName} ${user.lastName} (${user.email}) sera définitivement supprimé. Cette action est irréversible.`,
        confirmLabel: 'Supprimer',
        danger: true,
      },
      () => {
        this.busyUserId.set(user.id);
        this.userService.deleteUser(user.id).subscribe({
          next: (response) => {
            this.busyUserId.set(null);
            this.snackBar.open(
              response.message || 'Utilisateur supprimé avec succès.',
              'Fermer',
              { duration: 4500, panelClass: 'snackbar-success' },
            );
            if (this.users().length === 1 && this.pageIndex() > 0) {
              this.pageIndex.update((page) => page - 1);
            }
            this.loadUsers();
          },
          error: () => this.busyUserId.set(null),
        });
      },
    );
  }

  private setStatus(user: User, enabled: boolean): void {
    this.busyUserId.set(user.id);
    this.userService.updateStatus(user.id, enabled).subscribe({
      next: (updatedUser) => {
        this.busyUserId.set(null);
        this.replaceUser(updatedUser);
        this.showSuccess(
          enabled ? 'Le compte a été activé.' : 'Le compte a été désactivé.',
        );
      },
      error: () => this.busyUserId.set(null),
    });
  }

  private replaceUser(updatedUser: User): void {
    this.users.update((users) =>
      users.map((user) => (user.id === updatedUser.id ? updatedUser : user)),
    );
  }

  private showSuccess(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 4000,
      panelClass: 'snackbar-success',
    });
  }

  private openConfirmation(
    data: ConfirmationDialogData,
    onConfirm: () => void,
    onCancel?: () => void,
  ): void {
    this.dialog
      .open<ConfirmationDialog, ConfirmationDialogData, boolean>(ConfirmationDialog, {
        data,
        width: '480px',
        autoFocus: 'dialog',
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          onConfirm();
        } else {
          onCancel?.();
        }
      });
  }
}
