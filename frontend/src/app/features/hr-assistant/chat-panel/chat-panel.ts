import { Component, computed, effect, ElementRef, input, output, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ConversationResponse } from '../../../core/models/hr/conversation.model';
import { HrMessageResponse } from '../../../core/models/hr/hr-message.model';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { LoadingSpinner } from '../../../shared/components/loading-spinner/loading-spinner';
import { MessageBubble } from '../message-bubble/message-bubble';
import { MessageComposer } from '../message-composer/message-composer';

@Component({
  selector: 'app-chat-panel',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    EmptyState,
    LoadingSpinner,
    MessageBubble,
    MessageComposer,
  ],
  templateUrl: './chat-panel.html',
  styleUrl: './chat-panel.scss',
})
export class ChatPanel {
  readonly conversation = input<ConversationResponse | null>(null);
  readonly messages = input.required<readonly HrMessageResponse[]>();
  readonly loading = input(false);
  readonly sending = input(false);
  readonly errorMessage = input<string | null>(null);
  readonly resetToken = input(0);

  readonly back = output<void>();
  readonly create = output<void>();
  readonly rename = output<ConversationResponse>();
  readonly remove = output<ConversationResponse>();
  readonly send = output<string>();

  protected readonly hasConversation = computed(() => this.conversation() !== null);
  protected readonly messageCount = computed(() => this.messages().length);

  private readonly messagesEnd = viewChild<ElementRef<HTMLElement>>('messagesEnd');
  private readonly lastMessageId = computed(
    () => this.messages().at(-1)?.id ?? null,
  );

  constructor() {
    effect(() => {
      this.lastMessageId();
      const target = this.messagesEnd()?.nativeElement;
      if (target && typeof target.scrollIntoView === 'function') {
        queueMicrotask(() => target.scrollIntoView({ behavior: 'smooth', block: 'end' }));
      }
    });
  }

  protected requestRename(): void {
    const conversation = this.conversation();
    if (conversation) {
      this.rename.emit(conversation);
    }
  }

  protected requestDelete(): void {
    const conversation = this.conversation();
    if (conversation) {
      this.remove.emit(conversation);
    }
  }
}
