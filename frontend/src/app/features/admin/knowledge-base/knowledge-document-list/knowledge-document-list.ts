import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectChange, MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { environment } from '../../../../../environments/environment';
import {
  KNOWLEDGE_CATEGORY_OPTIONS,
  KnowledgeCategory,
} from '../../../../core/models/hr/knowledge-category.model';
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

type StatusFilter = 'all' | 'active' | 'inactive';

@Component({
  selector: 'app-knowledge-document-list',
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    MatTooltipModule,
    DatePipe,
    PageHeader,
    LoadingSpinner,
    EmptyState,
    KnowledgeCategoryPipe,
  ],
  templateUrl: './knowledge-document-list.html',
  styleUrl: './knowledge-document-list.scss',
})
export class KnowledgeDocumentList implements OnInit {
  private readonly knowledgeBaseService = inject(KnowledgeBaseService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly documents = signal<KnowledgeDocumentResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly totalElements = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal<number>(environment.defaultPageSize);
  protected readonly sortActive = signal('updatedAt');
  protected readonly sortDirection = signal<'asc' | 'desc'>('desc');
  protected readonly busyId = signal<number | null>(null);
  protected readonly reindexingAll = signal(false);

  protected readonly searchTerm = signal('');
  protected readonly categoryFilter = signal<KnowledgeCategory | ''>('');
  protected readonly statusFilter = signal<StatusFilter>('all');

  protected readonly pageSizeOptions: number[] = [...environment.pageSizeOptions];
  protected readonly categoryOptions = KNOWLEDGE_CATEGORY_OPTIONS;
  protected readonly displayedColumns: string[] = [
    'id',
    'title',
    'category',
    'active',
    'indexingStatus',
    'updatedAt',
    'createdById',
    'actions',
  ];

  protected readonly hasFilters = computed(
    () =>
      this.searchTerm().trim().length > 0 ||
      this.categoryFilter() !== '' ||
      this.statusFilter() !== 'all',
  );

  ngOnInit(): void {
    this.loadDocuments(0, this.pageSize());
  }

  protected loadDocuments(page = this.pageIndex(), size = this.pageSize()): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const status = this.statusFilter();
    this.knowledgeBaseService
      .getAdminDocuments({
        page,
        size,
        sort: `${this.sortActive()},${this.sortDirection()}`,
        category: this.categoryFilter() || undefined,
        active: status === 'all' ? undefined : status === 'active',
        search: this.searchTerm().trim() || undefined,
      })
      .subscribe({
        next: (response) => {
          this.documents.set(response.content);
          this.totalElements.set(response.totalElements);
          this.pageIndex.set(response.page);
          this.pageSize.set(response.size);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(
            getApiErrorMessage(
              error,
              'Impossible de charger la base de connaissances RH.',
            ),
          );
        },
      });
  }

  protected onSearchInput(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value);
  }

  protected onCategoryChange(change: MatSelectChange): void {
    this.categoryFilter.set(change.value as KnowledgeCategory | '');
    this.applyFilters();
  }

  protected onStatusChange(change: MatSelectChange): void {
    this.statusFilter.set(change.value as StatusFilter);
    this.applyFilters();
  }

  protected applyFilters(): void {
    this.pageIndex.set(0);
    this.loadDocuments(0, this.pageSize());
  }

  protected clearFilters(): void {
    this.searchTerm.set('');
    this.categoryFilter.set('');
    this.statusFilter.set('all');
    this.applyFilters();
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadDocuments(event.pageIndex, event.pageSize);
  }

  protected onSort(sort: Sort): void {
    this.sortActive.set(sort.active || 'updatedAt');
    this.sortDirection.set(sort.direction === 'asc' ? 'asc' : 'desc');
    this.pageIndex.set(0);
    this.loadDocuments(0, this.pageSize());
  }

  protected toggleStatus(document: KnowledgeDocumentResponse): void {
    if (this.busyId() !== null || this.reindexingAll()) {
      return;
    }

    const active = !document.active;
    const data: ConfirmationDialogData = {
      title: active ? 'Activer la ressource' : 'Désactiver la ressource',
      message: active
        ? `La ressource « ${document.title} » redeviendra visible par les candidats et les recruteurs.`
        : `La ressource « ${document.title} » ne sera plus visible par les candidats et les recruteurs. Vous pourrez la réactiver plus tard.`,
      confirmLabel: active ? 'Activer' : 'Désactiver',
      danger: !active,
    };

    this.openConfirmation(data, () => {
      this.busyId.set(document.id);
      this.knowledgeBaseService
        .updateDocumentStatus(document.id, active)
        .subscribe({
          next: () => {
            this.busyId.set(null);
            this.showSuccess(
              active ? 'Ressource activée.' : 'Ressource désactivée.',
            );
            this.loadDocuments();
          },
          error: (error: unknown) => {
            this.busyId.set(null);
            this.errorMessage.set(
              getApiErrorMessage(error, 'Impossible de modifier le statut de la ressource.'),
            );
          },
        });
    });
  }

  protected reindexDocument(document: KnowledgeDocumentResponse): void {
    if (this.busyId() !== null || this.reindexingAll()) {
      return;
    }

    this.busyId.set(document.id);
    this.errorMessage.set(null);
    this.knowledgeBaseService.reindexDocument(document.id).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.replaceDocument(updated);
        if (updated.indexingStatus === 'INDEXED') {
          this.showSuccess('Ressource réindexée.');
        } else {
          this.showError(
            updated.indexingError ?? "La réindexation de la ressource a échoué.",
          );
        }
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.errorMessage.set(
          getApiErrorMessage(
            error,
            'Impossible de réindexer la ressource. Le service vectoriel est peut-être indisponible.',
          ),
        );
      },
    });
  }

  protected reindexAll(): void {
    if (this.busyId() !== null || this.reindexingAll()) {
      return;
    }

    this.openConfirmation(
      {
        title: 'Réindexer toutes les ressources actives',
        message:
          'Les documents actifs seront renvoyés au service de recherche sémantique. Les documents inactifs resteront hors de l’index.',
        confirmLabel: 'Réindexer tout',
      },
      () => {
        this.reindexingAll.set(true);
        this.errorMessage.set(null);
        this.knowledgeBaseService.reindexAllDocuments().subscribe({
          next: (result) => {
            this.reindexingAll.set(false);
            if (result.failed > 0) {
              this.errorMessage.set(
                `${result.failed} ressource(s) n’ont pas pu être réindexées.`,
              );
              this.showError(
                `Réindexation terminée : ${result.indexed} réussie(s), ${result.failed} échec(s).`,
              );
            } else {
              this.showSuccess(
                `Réindexation terminée : ${result.indexed} ressource(s) indexée(s).`,
              );
            }
            this.loadDocuments();
          },
          error: (error: unknown) => {
            this.reindexingAll.set(false);
            this.errorMessage.set(
              getApiErrorMessage(
                error,
                'Impossible de réindexer les ressources. Le service vectoriel est peut-être indisponible.',
              ),
            );
          },
        });
      },
    );
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

  protected deleteDocument(document: KnowledgeDocumentResponse): void {
    if (this.busyId() !== null || this.reindexingAll()) {
      return;
    }

    this.openConfirmation(
      {
        title: 'Supprimer la ressource',
        message: `La ressource « ${document.title} » sera définitivement supprimée. La désactivation est recommandée si vous souhaitez conserver son contenu.`,
        confirmLabel: 'Supprimer',
        danger: true,
      },
      () => {
        this.busyId.set(document.id);
        this.knowledgeBaseService.deleteDocument(document.id).subscribe({
          next: () => {
            this.busyId.set(null);
            this.showSuccess('Ressource supprimée.');
            if (this.documents().length === 1 && this.pageIndex() > 0) {
              this.pageIndex.update((page) => page - 1);
            }
            this.loadDocuments();
          },
          error: (error: unknown) => {
            this.busyId.set(null);
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

  private replaceDocument(updated: KnowledgeDocumentResponse): void {
    this.documents.update((documents) =>
      documents.map((document) =>
        document.id === updated.id ? updated : document,
      ),
    );
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
