import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  IndexingStatus,
  KnowledgeDocumentResponse,
} from '../../../../core/models/hr/knowledge-document.model';
import { KnowledgeBaseService } from '../../../../core/services/knowledge-base.service';
import { getApiErrorMessage } from '../../../../core/utils/http-error.util';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../../../shared/components/confirmation-dialog/confirmation-dialog';
import { EmptyState } from '../../../../shared/components/empty-state/empty-state';
import { LoadingSpinner } from '../../../../shared/components/loading-spinner/loading-spinner';
import { PageHeader } from '../../../../shared/components/page-header/page-header';
import { KnowledgeCategoryPipe } from '../../../../shared/pipes/knowledge-category.pipe';

@Component({
  selector: 'app-knowledge-document-details',
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatDividerModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    DatePipe,
    PageHeader,
    LoadingSpinner,
    EmptyState,
    KnowledgeCategoryPipe,
  ],
  templateUrl: './knowledge-document-details.html',
  styleUrl: './knowledge-document-details.scss',
})
export class KnowledgeDocumentDetails implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly knowledgeBaseService = inject(KnowledgeBaseService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly documentId = Number(this.route.snapshot.paramMap.get('id'));
  protected readonly document = signal<KnowledgeDocumentResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    if (!Number.isInteger(this.documentId) || this.documentId <= 0) {
      this.loading.set(false);
      this.errorMessage.set('Identifiant de ressource invalide.');
      return;
    }
    this.loadDocument();
  }

  protected loadDocument(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.knowledgeBaseService.getDocumentById(this.documentId).subscribe({
      next: (document) => {
        this.document.set(document);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.snackBar.open(
            "Cette ressource n'existe plus. Retour à la liste.",
            'Fermer',
            { duration: 4500, panelClass: 'snackbar-error' },
          );
          void this.router.navigate(['/admin/knowledge-base']);
          return;
        }
        this.errorMessage.set(
          getApiErrorMessage(error, 'Impossible de charger cette ressource.'),
        );
      },
    });
  }

  protected toggleStatus(): void {
    const document = this.document();
    if (!document || this.busy()) {
      return;
    }

    const active = !document.active;
    this.openConfirmation(
      {
        title: active ? 'Activer la ressource' : 'Désactiver la ressource',
        message: active
          ? `La ressource « ${document.title} » redeviendra visible par les candidats et les recruteurs.`
          : `La ressource « ${document.title} » ne sera plus visible dans le front-office. Vous pourrez la réactiver à tout moment.`,
        confirmLabel: active ? 'Activer' : 'Désactiver',
        danger: !active,
      },
      () => {
        this.busy.set(true);
        this.knowledgeBaseService
          .updateDocumentStatus(document.id, active)
          .subscribe({
            next: (updated) => {
              this.document.set(updated);
              this.busy.set(false);
              this.showSuccess(
                active ? 'Ressource activée.' : 'Ressource désactivée.',
              );
            },
            error: (error: unknown) => {
              this.busy.set(false);
              this.errorMessage.set(
                getApiErrorMessage(
                  error,
                  'Impossible de modifier le statut de la ressource.',
                ),
              );
            },
          });
      },
    );
  }

  protected reindexDocument(): void {
    const document = this.document();
    if (!document || this.busy() || !document.active) {
      return;
    }

    this.busy.set(true);
    this.knowledgeBaseService.reindexDocument(document.id).subscribe({
      next: (updated) => {
        this.document.set(updated);
        this.busy.set(false);
        if (updated.indexingStatus === 'INDEXED') {
          this.showSuccess('Ressource réindexée.');
        } else {
          this.showError(
            updated.indexingError ?? "La réindexation de la ressource a échoué.",
          );
        }
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.showError(
          getApiErrorMessage(
            error,
            'Impossible de réindexer la ressource. Le service vectoriel est peut-être indisponible.',
          ),
        );
      },
    });
  }

  protected indexingStatusLabel(status: IndexingStatus): string {
    switch (status) {
      case 'PENDING':
        return 'Indexation en attente';
      case 'INDEXED':
        return 'Indexé';
      case 'FAILED':
        return 'Échec d’indexation';
      default:
        return 'Non indexé';
    }
  }

  protected indexingStatusIcon(status: IndexingStatus): string {
    switch (status) {
      case 'PENDING':
        return 'hourglass_top';
      case 'INDEXED':
        return 'cloud_done';
      case 'FAILED':
        return 'error';
      default:
        return 'cloud_off';
    }
  }

  protected indexingBadgeClass(status: IndexingStatus): string {
    switch (status) {
      case 'INDEXED':
        return 'badge--success';
      case 'FAILED':
        return 'badge--danger';
      case 'PENDING':
        return 'badge--warning';
      default:
        return 'badge--neutral';
    }
  }

  protected deleteDocument(): void {
    const document = this.document();
    if (!document || this.busy()) {
      return;
    }

    this.openConfirmation(
      {
        title: 'Supprimer la ressource',
        message: `La ressource « ${document.title} » sera définitivement supprimée. Cette action est irréversible.`,
        confirmLabel: 'Supprimer',
        danger: true,
      },
      () => {
        this.busy.set(true);
        this.knowledgeBaseService.deleteDocument(document.id).subscribe({
          next: () => {
            this.busy.set(false);
            this.showSuccess('Ressource supprimée.');
            void this.router.navigate(['/admin/knowledge-base']);
          },
          error: (error: unknown) => {
            this.busy.set(false);
            this.errorMessage.set(
              getApiErrorMessage(error, 'Impossible de supprimer la ressource.'),
            );
          },
        });
      },
    );
  }

  private openConfirmation(data: ConfirmationDialogData, onConfirm: () => void): void {
    this.dialog
      .open<ConfirmationDialog, ConfirmationDialogData, boolean>(ConfirmationDialog, {
        data,
        width: '500px',
        autoFocus: 'dialog',
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          onConfirm();
        }
      });
  }

  private showSuccess(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 3500,
      panelClass: 'snackbar-success',
    });
  }

  private showError(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 5500,
      panelClass: 'snackbar-error',
    });
  }
}
