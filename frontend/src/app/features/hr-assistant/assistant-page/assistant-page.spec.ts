import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ConversationResponse } from '../../../core/models/hr/conversation.model';
import { HrMessageResponse } from '../../../core/models/hr/hr-message.model';
import { HrConversationService } from '../../../core/services/hr-conversation.service';
import { ConversationItem } from '../conversation-item/conversation-item';
import { AssistantPage } from './assistant-page';

const CONVERSATION_ONE: ConversationResponse = {
  id: 1,
  title: 'Préparation entretien',
  createdAt: '2026-10-10T09:00:00',
  updatedAt: '2026-10-10T10:00:00',
};

const CONVERSATION_TWO: ConversationResponse = {
  id: 2,
  title: 'Politique de congés',
  createdAt: '2026-10-11T09:00:00',
  updatedAt: '2026-10-11T10:00:00',
};

function message(
  id: number,
  conversationId: number,
  content: string,
  role: HrMessageResponse['role'] = 'USER',
): HrMessageResponse {
  return {
    id,
    conversationId,
    content,
    role,
    createdAt: '2026-10-12T09:30:00',
    sources: [],
  };
}

function assistantResponse(userMessage: HrMessageResponse, content = 'Réponse fondée.') {
  return {
    userMessage,
    assistantMessage: {
      id: userMessage.id + 1,
      conversationId: userMessage.conversationId,
      role: 'ASSISTANT' as const,
      content,
      createdAt: '2026-10-12T09:31:00',
      sources: [
        {
          documentId: 10,
          chunkId: 'document-10-v1-chunk-0',
          chunkIndex: 0,
          title: 'Comment postuler à une offre',
          category: 'PLATFORM_HELP' as const,
          score: 0.74,
          documentVersion: 1,
          excerpt: 'Pour postuler, consultez les offres…',
        },
      ],
    },
    sources: [],
    grounded: true,
    needsHumanReview: false,
    modelName: 'qwen3:4b',
    promptVersion: 'hr-rag-v1',
    durationMs: 1234,
  };
}

function page(content: ConversationResponse[]) {
  return {
    content,
    page: 0,
    size: 100,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    last: true,
  };
}

describe('AssistantPage', () => {
  let fixture: ComponentFixture<AssistantPage>;
  let conversationService: {
    getConversations: ReturnType<typeof vi.fn>;
    getConversation: ReturnType<typeof vi.fn>;
    createConversation: ReturnType<typeof vi.fn>;
    renameConversation: ReturnType<typeof vi.fn>;
    deleteConversation: ReturnType<typeof vi.fn>;
    getMessages: ReturnType<typeof vi.fn>;
    ask: ReturnType<typeof vi.fn>;
    sendMessage: ReturnType<typeof vi.fn>;
  };
  let snackMock: { open: ReturnType<typeof vi.fn> };
  let dialogResults: unknown[];
  let dialogMock: { open: ReturnType<typeof vi.fn> };

  async function setup(
    conversations: ConversationResponse[],
    messagesByConversation: Record<number, HrMessageResponse[]> = {},
  ): Promise<void> {
    conversationService = {
      getConversations: vi.fn().mockReturnValue(of(page(conversations))),
      getConversation: vi.fn(),
      createConversation: vi.fn(),
      renameConversation: vi.fn(),
      deleteConversation: vi.fn().mockReturnValue(of(void 0)),
      getMessages: vi.fn().mockImplementation((id: number) =>
        of(messagesByConversation[id] ?? []),
      ),
      ask: vi.fn(),
      sendMessage: vi.fn(),
    };
    snackMock = { open: vi.fn() };
    dialogResults = [];
    dialogMock = {
      open: vi.fn().mockImplementation(() => ({
        afterClosed: () => of(dialogResults.shift()),
      })),
    };

    await TestBed.configureTestingModule({
      imports: [AssistantPage],
      providers: [
        { provide: HrConversationService, useValue: conversationService },
        { provide: MatDialog, useValue: dialogMock },
        { provide: MatSnackBar, useValue: snackMock },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialogMock });
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(AssistantPage);
    fixture.detectChanges();
  }

  function textarea(): HTMLTextAreaElement {
    return fixture.debugElement.query(By.css('app-message-composer textarea'))
      .nativeElement as HTMLTextAreaElement;
  }

  function submitMessage(value: string): void {
    const field = textarea();
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.debugElement
      .query(By.css('app-message-composer form'))
      .nativeElement.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('charge les conversations et sélectionne automatiquement la première', async () => {
    await setup([CONVERSATION_ONE, CONVERSATION_TWO], {
      1: [message(10, 1, 'Bonjour, comment préparer mon entretien ?')],
    });

    expect(conversationService.getConversations).toHaveBeenCalled();
    expect(conversationService.getMessages).toHaveBeenCalledWith(1);
    expect(fixture.nativeElement.textContent).toContain('Préparation entretien');
    expect(fixture.nativeElement.textContent).toContain(
      'Bonjour, comment préparer mon entretien ?',
    );
  });

  it('change de conversation et charge son historique', async () => {
    await setup([CONVERSATION_ONE, CONVERSATION_TWO], {
      1: [message(10, 1, 'Message de la première')],
      2: [message(11, 2, 'Message de la deuxième')],
    });

    const items = fixture.debugElement.queryAll(By.css('.conversation-item__main'));
    items[1].nativeElement.click();
    fixture.detectChanges();

    expect(conversationService.getMessages).toHaveBeenCalledWith(2);
    expect(fixture.nativeElement.textContent).toContain('Message de la deuxième');
  });

  it('crée une conversation, la sélectionne et affiche son état vide', async () => {
    await setup([]);
    const created = {
      ...CONVERSATION_ONE,
      id: 8,
      title: 'Nouvelle conversation',
    };
    conversationService.createConversation.mockReturnValue(of(created));

    fixture.debugElement.query(By.css('.conversation-list__new')).nativeElement.click();
    fixture.detectChanges();

    expect(conversationService.createConversation).toHaveBeenCalledWith({
      title: 'Nouvelle conversation',
    });
    expect(fixture.nativeElement.textContent).toContain('Nouvelle conversation');
    expect(fixture.nativeElement.textContent).toContain(
      "Posez votre première question à l'Assistant RH.",
    );
  });

  it('refuse l’envoi d’un message vide sans appeler l’API', async () => {
    await setup([CONVERSATION_ONE]);

    submitMessage('   ');

    expect(conversationService.ask).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('ne peut pas être vide');
  });

  it('appelle /ask et affiche les messages USER et ASSISTANT renvoyés', async () => {
    await setup([CONVERSATION_ONE]);
    const sent = message(21, 1, 'Ma question RH');
    conversationService.ask.mockReturnValue(
      of(assistantResponse(sent, 'Consultez les offres depuis votre espace candidat.')),
    );

    submitMessage('  Ma question RH  ');

    expect(conversationService.ask).toHaveBeenCalledWith(1, {
      content: 'Ma question RH',
    });
    expect(conversationService.sendMessage).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Ma question RH');
    expect(fixture.nativeElement.textContent).toContain(
      'Consultez les offres depuis votre espace candidat.',
    );
    expect(fixture.debugElement.queryAll(By.css('app-message-bubble')).length).toBe(2);
    expect(fixture.debugElement.query(By.css('.message--assistant'))).toBeTruthy();
    expect(textarea().value).toBe('');
  });

  it('affiche un état de génération et désactive le composer pendant /ask', async () => {
    await setup([CONVERSATION_ONE]);
    const subject = new Subject<ReturnType<typeof assistantResponse>>();
    conversationService.ask.mockReturnValue(subject.asObservable());

    submitMessage('Comment postuler ?');

    expect(fixture.nativeElement.textContent).toContain(
      'L’Assistant RH prépare une réponse…',
    );
    expect(textarea().disabled).toBe(true);

    subject.next(
      assistantResponse(
        message(22, 1, 'Comment postuler ?'),
        'Consultez les offres.',
      ),
    );
    subject.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain(
      'L’Assistant RH prépare une réponse…',
    );
    expect(textarea().disabled).toBe(false);
  });

  it('affiche les sources avec une similarité, pas une confiance', async () => {
    await setup([CONVERSATION_ONE]);
    const sent = message(23, 1, 'Comment postuler ?');
    conversationService.ask.mockReturnValue(of(assistantResponse(sent)));

    submitMessage('Comment postuler ?');

    expect(fixture.nativeElement.textContent).toContain('Sources utilisées');
    expect(fixture.nativeElement.textContent).toContain(
      'Comment postuler à une offre',
    );
    expect(fixture.nativeElement.textContent).toContain('Similarité : 74 %');
    expect(fixture.nativeElement.textContent).not.toContain('Confiance');
  });

  it('affiche un repli sans source pour une réponse non fondée', async () => {
    await setup([CONVERSATION_ONE]);
    const sent = message(24, 1, 'Quel temps fera-t-il demain ?');
    const response = assistantResponse(
      sent,
      'Je ne dispose pas d’informations suffisantes dans la base de connaissances RH.',
    );
    response.assistantMessage.sources = [];
    response.sources = [];
    response.grounded = false;
    response.needsHumanReview = true;
    conversationService.ask.mockReturnValue(of(response));

    submitMessage('Quel temps fera-t-il demain ?');

    expect(fixture.nativeElement.textContent).toContain(
      'Aucune source pertinente trouvée.',
    );
    expect(fixture.nativeElement.textContent).not.toContain('Sources utilisées');
  });

  it('affiche une erreur 503 et recharge le message USER sans bulle ASSISTANT fictive', async () => {
    await setup([CONVERSATION_ONE]);
    const persistedUser = message(25, 1, 'Comment postuler ?');
    conversationService.ask.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: {
              message:
                "L'Assistant RH est temporairement indisponible. Votre message a été enregistré. Vous pouvez réessayer.",
            },
          }),
      ),
    );
    conversationService.getMessages.mockReturnValue(of([persistedUser]));

    submitMessage('Comment postuler ?');

    expect(fixture.nativeElement.textContent).toContain(
      "L'Assistant RH est temporairement indisponible.",
    );
    expect(fixture.nativeElement.textContent).toContain('Comment postuler ?');
    expect(fixture.debugElement.query(By.css('.message--assistant'))).toBeNull();
    expect(textarea().value).toBe('Comment postuler ?');
  });

  it('supprime une conversation et sélectionne la suivante', async () => {
    await setup([CONVERSATION_ONE, CONVERSATION_TWO], {
      1: [message(10, 1, 'Première')],
      2: [message(11, 2, 'Deuxième')],
    });
    dialogResults.push(true);

    const items = fixture.debugElement.queryAll(By.directive(ConversationItem));
    (items[0].componentInstance as ConversationItem).remove.emit(CONVERSATION_ONE);
    fixture.detectChanges();

    expect(conversationService.deleteConversation).toHaveBeenCalledWith(1);
    expect(conversationService.getMessages).toHaveBeenCalledWith(2);
    expect(fixture.nativeElement.textContent).not.toContain('Préparation entretien');
    expect(fixture.nativeElement.textContent).toContain('Politique de congés');
  });

  it('renomme une conversation et met à jour le titre affiché', async () => {
    await setup([CONVERSATION_ONE]);
    dialogResults.push('Entretien Java');
    conversationService.renameConversation.mockReturnValue(
      of({ ...CONVERSATION_ONE, title: 'Entretien Java' }),
    );

    const item = fixture.debugElement.query(By.directive(ConversationItem));
    (item.componentInstance as ConversationItem).rename.emit(CONVERSATION_ONE);
    fixture.detectChanges();

    expect(conversationService.renameConversation).toHaveBeenCalledWith(1, {
      title: 'Entretien Java',
    });
    expect(fixture.nativeElement.textContent).toContain('Entretien Java');
  });

  it('revient à la liste lorsqu’une conversation n’existe plus (404)', async () => {
    conversationService = {
      getConversations: vi.fn().mockReturnValue(of(page([CONVERSATION_ONE]))),
      getConversation: vi.fn(),
      createConversation: vi.fn(),
      renameConversation: vi.fn(),
      deleteConversation: vi.fn(),
      getMessages: vi
        .fn()
        .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 }))),
      ask: vi.fn(),
      sendMessage: vi.fn(),
    };
    snackMock = { open: vi.fn() };
    dialogMock = { open: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [AssistantPage],
      providers: [
        { provide: HrConversationService, useValue: conversationService },
        { provide: MatDialog, useValue: dialogMock },
        { provide: MatSnackBar, useValue: snackMock },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialogMock });
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(AssistantPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      "Cette conversation n'est plus disponible",
    );
    expect(fixture.nativeElement.textContent).toContain('Sélectionnez une conversation');
  });

  it('reste rendu sans erreur aux largeurs desktop et mobile', async () => {
    await setup([CONVERSATION_ONE]);

    Object.defineProperty(window, 'innerWidth', { value: 390, configurable: true });
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.workspace__list'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('.workspace__chat'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('app-admin-sidebar'))).toBeNull();

    Object.defineProperty(window, 'innerWidth', { value: 1440, configurable: true });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Assistant RH');
  });
});
