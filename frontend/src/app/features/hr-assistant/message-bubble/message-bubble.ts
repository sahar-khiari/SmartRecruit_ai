import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { HrMessageResponse } from '../../../core/models/hr/hr-message.model';
import {
  KnowledgeCategory,
  knowledgeCategoryLabel,
} from '../../../core/models/hr/knowledge-category.model';
import { MessageSourceResponse } from '../../../core/models/hr/message-source.model';

@Component({
  selector: 'app-message-bubble',
  imports: [DatePipe, MatIconModule],
  templateUrl: './message-bubble.html',
  styleUrl: './message-bubble.scss',
})
export class MessageBubble {
  readonly message = input.required<HrMessageResponse>();

  protected readonly isUser = computed(() => this.message().role === 'USER');
  protected readonly isAssistant = computed(() => this.message().role === 'ASSISTANT');
  protected readonly isSystem = computed(() => this.message().role === 'SYSTEM');
  protected readonly sources = computed<MessageSourceResponse[]>(
    () => this.message().sources ?? [],
  );

  protected readonly authorLabel = computed(() => {
    switch (this.message().role) {
      case 'USER':
        return 'Vous';
      case 'ASSISTANT':
        return 'Assistant RH';
      default:
        return 'Système';
    }
  });

  protected formatScore(score: number): number {
    const normalized = Math.max(0, Math.min(1, score));
    return Math.round(normalized * 100);
  }

  protected categoryLabel(category: KnowledgeCategory): string {
    return knowledgeCategoryLabel(category);
  }
}
