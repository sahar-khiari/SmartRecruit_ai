import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConversationResponse } from '../../../core/models/hr/conversation.model';
import { HrMessageResponse } from '../../../core/models/hr/hr-message.model';
import { HrConversationService } from '../../../core/services/hr-conversation.service';
import { getApiErrorMessage } from '../../../core/utils/http-error.util';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../../shared/components/confirmation-dialog/confirmation-dialog';
import { PageHeader } from '../../../shared/components/page-header/page-header';
import { ChatPanel } from '../chat-panel/chat-panel';
import { ConversationList } from '../conversation-list/conversation-list';
import {
  RenameConversationDialog,
  RenameConversationDialogData,
} from '../rename-conversation-dialog/rename-conversation-dialog';

@Component({
  selector: 'app-assistant-page',
  imports: [PageHeader, ConversationList, ChatPanel, MatIconModule],
  templateUrl: './assistant-page.html',
  styleUrl: './assistant-page.scss',
})
export class AssistantPage implements OnInit {
  private readonly conversationService = inject(HrConversationService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly conversations = signal<ConversationResponse[]>([]);
  protected readonly messages = signal<HrMessageResponse[]>([]);
  protected readonly selectedId = signal<number | null>(null);
  protected readonly loading = signal(true);
  protected readonly messageLoading = signal(false);
  protected readonly creating = signal(false);
  protected readonly sending = signal(false);
  protected readonly listError = signal<string | null>(null);
  protected readonly chatError = signal<string | null>(null);
  protected readonly resetToken = signal(0);
  protected readonly mobileChatOpen = signal(false);

  protected readonly selectedConversation = computed(
    () => this.conversations().find((item) => item.id === this.selectedId()) ?? null,
  );

  ngOnInit(): void {
    this.loadConversations(true);
  }

  protected loadConversations(selectFirst: boolean): void {
    this.loading.set(true);
    this.listError.set(null);

    this.conversationService.getConversations().subscribe({
      next: (response) => {
        this.loading.set(false);
        this.conversations.set(response.content);

        const currentId = this.selectedId();
        const currentStillExists =
          currentId !== null && response.content.some((item) => item.id === currentId);

        if (currentStillExists) {
          return;
        }

        if (selectFirst && response.content.length > 0) {
          this.selectConversation(response.content[0].id);
          return;
        }

        this.selectedId.set(null);
        this.messages.set([]);
        this.mobileChatOpen.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.listError.set(
          getApiErrorMessage(error, 'Impossible de charger vos conversations.'),
        );
      },
    });
  }

  protected selectConversation(id: number): void {
    this.mobileChatOpen.set(true);

    if (this.selectedId() === id && this.messages().length > 0) {
      return;
    }

    this.selectedId.set(id);
    this.chatError.set(null);
    this.messages.set([]);
    this.loadMessages(id);
  }

  protected createConversation(): void {
    if (this.creating()) {
      return;
    }

    this.creating.set(true);
    this.listError.set(null);

    this.conversationService
      .createConversation({ title: 'Nouvelle conversation' })
      .subscribe({
        next: (conversation) => {
          this.creating.set(false);
          this.conversations.update((items) => [conversation, ...items]);
          this.selectedId.set(conversation.id);
          this.messages.set([]);
          this.chatError.set(null);
          this.mobileChatOpen.set(true);
          this.showSuccess('Conversation créée.');
        },
        error: (error: unknown) => {
          this.creating.set(false);
          this.listError.set(
            getApiErrorMessage(error, 'Impossible de créer la conversation.'),
          );
        },
      });
  }

  protected renameConversation(conversation: ConversationResponse): void {
    this.dialog
      .open<
        RenameConversationDialog,
        RenameConversationDialogData,
        string | undefined
      >(RenameConversationDialog, {
        data: { title: conversation.title },
        width: '480px',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((title) => {
        if (!title || title === conversation.title) {
          return;
        }

        this.conversationService
          .renameConversation(conversation.id, { title })
          .subscribe({
            next: (updated) => {
              this.conversations.update((items) =>
                this.sortConversations(
                  items.map((item) => (item.id === updated.id ? updated : item)),
                ),
              );
              this.showSuccess('Conversation renommée.');
            },
            error: (error: unknown) => {
              this.listError.set(
                getApiErrorMessage(error, 'Impossible de renommer la conversation.'),
              );
            },
          });
      });
  }

  protected deleteConversation(conversation: ConversationResponse): void {
    const data: ConfirmationDialogData = {
      title: 'Supprimer la conversation',
      message: `La conversation « ${conversation.title} » et tous ses messages seront définitivement supprimés. Cette action est irréversible.`,
      confirmLabel: 'Supprimer',
      danger: true,
    };

    this.dialog
      .open<ConfirmationDialog, ConfirmationDialogData, boolean>(ConfirmationDialog, {
        data,
        width: '480px',
        autoFocus: 'dialog',
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }

        this.conversationService.deleteConversation(conversation.id).subscribe({
          next: () => {
            const remaining = this.conversations().filter(
              (item) => item.id !== conversation.id,
            );
            this.conversations.set(remaining);

            if (this.selectedId() === conversation.id) {
              this.selectedId.set(null);
              this.messages.set([]);
              this.chatError.set(null);

              if (remaining.length > 0) {
                this.selectConversation(remaining[0].id);
              } else {
                this.mobileChatOpen.set(false);
              }
            }

            this.showSuccess('Conversation supprimée.');
          },
          error: (error: unknown) => {
            this.listError.set(
              getApiErrorMessage(error, 'Impossible de supprimer la conversation.'),
            );
          },
        });
      });
  }

  protected sendMessage(content: string): void {
    const conversation = this.selectedConversation();
    if (!conversation || this.sending()) {
      return;
    }

    this.sending.set(true);
    this.chatError.set(null);

    this.conversationService
      .ask(conversation.id, { content })
      .subscribe({
        next: (response) => {
          // Le backend renvoie les deux messages persistés : aucune bulle ASSISTANT
          // n'est créée côté client avant la réponse réelle.
          this.messages.update((items) => [
            ...items,
            response.userMessage,
            response.assistantMessage,
          ]);
          this.sending.set(false);
          this.resetToken.update((value) => value + 1);
        },
        error: (error: unknown) => {
          this.sending.set(false);
          this.chatError.set(
            getApiErrorMessage(
              error,
              "L'Assistant RH est temporairement indisponible. "
                + 'Votre message a été enregistré. Vous pouvez réessayer.',
            ),
          );
          // Le message USER a pu être enregistré avant l'échec FastAPI/Ollama.
          // On recharge l'historique pour refléter l'état réel sans inventer de réponse.
          this.loadMessages(conversation.id);
        },
      });
  }

  protected backToList(): void {
    this.mobileChatOpen.set(false);
  }

  private loadMessages(conversationId: number): void {
    this.messageLoading.set(true);
    this.conversationService.getMessages(conversationId).subscribe({
      next: (messages) => {
        if (this.selectedId() !== conversationId) {
          return;
        }
        this.messages.set(messages);
        this.messageLoading.set(false);
      },
      error: (error: unknown) => {
        this.messageLoading.set(false);

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.selectedId.set(null);
          this.messages.set([]);
          this.mobileChatOpen.set(false);
          this.loadConversations(false);
          this.listError.set(
            "Cette conversation n'est plus disponible. La liste a été actualisée.",
          );
          return;
        }

        this.chatError.set(
          getApiErrorMessage(error, 'Impossible de charger les messages.'),
        );
      },
    });
  }

  private sortConversations(
    conversations: ConversationResponse[],
  ): ConversationResponse[] {
    return [...conversations].sort((a, b) =>
      b.updatedAt.localeCompare(a.updatedAt),
    );
  }

  private showSuccess(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 3500,
      panelClass: 'snackbar-success',
    });
  }
}
