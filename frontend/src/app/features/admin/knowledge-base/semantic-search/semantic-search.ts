import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import {
  KNOWLEDGE_CATEGORY_OPTIONS,
  KnowledgeCategory,
  knowledgeCategoryLabel,
} from '../../../../core/models/hr/knowledge-category.model';
import {
  RetrievalSearchResponse,
} from '../../../../core/models/hr/knowledge-document.model';
import { KnowledgeBaseService } from '../../../../core/services/knowledge-base.service';
import { getApiErrorMessage } from '../../../../core/utils/http-error.util';
import { EmptyState } from '../../../../shared/components/empty-state/empty-state';
import { PageHeader } from '../../../../shared/components/page-header/page-header';

function nonEmptyText(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length > 0
    ? null
    : { required: true };
}

@Component({
  selector: 'app-semantic-search',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    DecimalPipe,
    PageHeader,
    EmptyState,
  ],
  templateUrl: './semantic-search.html',
  styleUrl: './semantic-search.scss',
})
export class SemanticSearch {
  private readonly knowledgeBaseService = inject(KnowledgeBaseService);

  protected readonly form = new FormGroup({
    query: new FormControl('', {
      nonNullable: true,
      validators: [nonEmptyText, Validators.maxLength(2000)],
    }),
    category: new FormControl<KnowledgeCategory | ''>('', { nonNullable: true }),
    topK: new FormControl(5, { nonNullable: true }),
  });

  protected readonly categoryOptions = KNOWLEDGE_CATEGORY_OPTIONS;
  protected readonly topKOptions = [3, 5, 10];
  protected readonly searching = signal(false);
  protected readonly response = signal<RetrievalSearchResponse | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hasSanitizedDifference = computed(() => {
    const result = this.response();
    return !!result && result.sanitizedQuery !== result.query;
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    this.searching.set(true);
    this.errorMessage.set(null);

    this.knowledgeBaseService
      .searchSemantic({
        query: value.query.trim(),
        topK: value.topK,
        categories: value.category ? [value.category] : undefined,
      })
      .subscribe({
        next: (result) => {
          this.response.set(result);
          this.searching.set(false);
        },
        error: (error: unknown) => {
          this.searching.set(false);
          this.response.set(null);
          this.errorMessage.set(
            getApiErrorMessage(
              error,
              'La recherche sémantique est indisponible. Vérifiez que le service interne est démarré.',
            ),
          );
        },
      });
  }

  protected reset(): void {
    this.form.reset({ query: '', category: '', topK: 5 });
    this.response.set(null);
    this.errorMessage.set(null);
  }

  protected categoryLabel(category: KnowledgeCategory): string {
    return knowledgeCategoryLabel(category);
  }

  protected scorePercent(score: number): string {
    return `${Math.round(score * 1000) / 10} %`;
  }
}
