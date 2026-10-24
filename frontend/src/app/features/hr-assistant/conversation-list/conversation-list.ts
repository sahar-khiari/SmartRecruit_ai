import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ConversationResponse } from '../../../core/models/hr/conversation.model';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { LoadingSpinner } from '../../../shared/components/loading-spinner/loading-spinner';
import { ConversationItem } from '../conversation-item/conversation-item';

@Component({
  selector: 'app-conversation-list',
  imports: [MatButtonModule, MatIconModule, EmptyState, LoadingSpinner, ConversationItem],
  templateUrl: './conversation-list.html',
  styleUrl: './conversation-list.scss',
})
export class ConversationList {
  readonly conversations = input.required<readonly ConversationResponse[]>();
  readonly selectedId = input<number | null>(null);
  readonly loading = input(false);
  readonly creating = input(false);
  readonly errorMessage = input<string | null>(null);

  readonly create = output<void>();
  readonly refresh = output<void>();
  readonly select = output<number>();
  readonly rename = output<ConversationResponse>();
  readonly remove = output<ConversationResponse>();
}
