import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  KNOWLEDGE_CATEGORY_OPTIONS,
  KnowledgeCategory,
} from '../../../../core/models/hr/knowledge-category.model';
import { KnowledgeDocumentResponse } from '../../../../core/models/hr/knowledge-document.model';
import { KnowledgeBaseService } from '../../../../core/services/knowledge-base.service';
import { HasUnsavedChanges } from '../../../../core/guards/unsaved-changes.guard';
import { getApiErrorMessage } from '../../../../core/utils/http-error.util';
import { EmptyState } from '../../../../shared/components/empty-state/empty-state';
import { LoadingSpinner } from '../../../../shared/components/loading-spinner/loading-spinner';
import { PageHeader } from '../../../../shared/components/page-header/page-header';
import { trimmedRequired } from '../../../hr-assistant/message-composer/message-composer';

export const KNOWLEDGE_TITLE_MAX_LENGTH = 200;
export const KNOWLEDGE_CONTENT_MAX_LENGTH = 100_000;

@Component({
  selector: 'app-knowledge-document-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    PageHeader,
    LoadingSpinner,
    EmptyState,
  ],
  templateUrl: './knowledge-document-form.html',
  styleUrl: './knowledge-document-form.scss',
})
export class KnowledgeDocumentForm implements OnInit, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly knowledgeBaseService = inject(KnowledgeBaseService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly documentId = Number(this.route.snapshot.paramMap.get('id'));
  protected readonly isEdit = Number.isInteger(this.documentId) && this.documentId > 0;

  protected readonly loading = signal(this.isEdit);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly document = signal<KnowledgeDocumentResponse | null>(null);

  protected readonly maxTitleLength = KNOWLEDGE_TITLE_MAX_LENGTH;
  protected readonly maxContentLength = KNOWLEDGE_CONTENT_MAX_LENGTH;
  protected readonly categoryOptions = KNOWLEDGE_CATEGORY_OPTIONS;

  protected readonly form = new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [trimmedRequired, Validators.maxLength(KNOWLEDGE_TITLE_MAX_LENGTH)],
    }),
    category: new FormControl<KnowledgeCategory | null>(null, {
      validators: [Validators.required],
    }),
    content: new FormControl('', {
      nonNullable: true,
      validators: [
        trimmedRequired,
        Validators.maxLength(KNOWLEDGE_CONTENT_MAX_LENGTH),
      ],
    }),
  });

  ngOnInit(): void {
    if (this.isEdit) {
      this.loadDocument();
    }
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this.saving();
  }

  protected loadDocument(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.knowledgeBaseService.getDocumentById(this.documentId).subscribe({
      next: (document) => {
        this.document.set(document);
        this.form.setValue({
          title: document.title,
          category: document.category,
          content: document.content,
        });
        this.form.markAsPristine();
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.snackBar.open(
            "Cette ressource n'existe plus. Retour à la liste.",
            'Fermer',
            { duration: 5000, panelClass: 'snackbar-error' },
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

  protected submit(): void {
    this.form.markAllAsTouched();

    if (this.form.invalid || this.saving() || this.loading()) {
      return;
    }

    const raw = this.form.getRawValue();
    const title = raw.title.trim();
    const content = raw.content.trim();

    if (!title || !content || !raw.category) {
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);

    const request = {
      title,
      content,
      category: raw.category,
    };

    const operation = this.isEdit
      ? this.knowledgeBaseService.updateDocument(this.documentId, request)
      : this.knowledgeBaseService.createDocument(request);

    operation.subscribe({
      next: (document) => {
        this.form.markAsPristine();
        this.document.set(document);
        this.saving.set(false);
        this.snackBar.open(
          this.isEdit ? 'Ressource mise à jour.' : 'Ressource créée.',
          'Fermer',
          { duration: 3500, panelClass: 'snackbar-success' },
        );
        void this.router.navigate(['/admin/knowledge-base', document.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(
          getApiErrorMessage(
            error,
            this.isEdit
              ? 'Impossible de modifier la ressource.'
              : 'Impossible de créer la ressource.',
          ),
        );
      },
    });
  }
}
