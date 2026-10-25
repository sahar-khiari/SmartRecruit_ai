import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSelect, MatSelectChange } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { KnowledgeDocumentResponse } from '../../../../core/models/hr/knowledge-document.model';
import { KnowledgeBaseService } from '../../../../core/services/knowledge-base.service';
import { KnowledgeDocumentList } from './knowledge-document-list';

const DOCUMENT: KnowledgeDocumentResponse = {
  id: 7,
  title: 'Comment postuler ?',
  content: 'Connectez-vous puis cliquez sur Postuler.',
  category: 'CANDIDATE_GUIDE',
  active: true,
  createdById: 1,
  createdAt: '2026-10-10T10:00:00',
  updatedAt: '2026-10-11T11:00:00',
  version: 0,
  indexingStatus: 'INDEXED',
  indexedAt: '2026-10-11T11:05:00',
  indexingError: null,
};

function page(content: KnowledgeDocumentResponse[]) {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    last: true,
  };
}

describe('KnowledgeDocumentList', () => {
  let fixture: ComponentFixture<KnowledgeDocumentList>;
  let component: KnowledgeDocumentList;
  let serviceMock: {
    getAdminDocuments: ReturnType<typeof vi.fn>;
    updateDocumentStatus: ReturnType<typeof vi.fn>;
    deleteDocument: ReturnType<typeof vi.fn>;
    reindexDocument: ReturnType<typeof vi.fn>;
    reindexAllDocuments: ReturnType<typeof vi.fn>;
  };
  let snackMock: { open: ReturnType<typeof vi.fn> };
  let dialogResults: unknown[];
  let dialogMock: { open: ReturnType<typeof vi.fn> };

  async function setup(documents: KnowledgeDocumentResponse[] = [DOCUMENT]): Promise<void> {
    serviceMock = {
      getAdminDocuments: vi.fn().mockReturnValue(of(page(documents))),
      updateDocumentStatus: vi.fn().mockReturnValue(of({ ...DOCUMENT, active: false })),
      deleteDocument: vi.fn().mockReturnValue(of(void 0)),
      reindexDocument: vi.fn().mockReturnValue(of(DOCUMENT)),
      reindexAllDocuments: vi.fn().mockReturnValue(
        of({
          total: 1,
          indexed: 1,
          failed: 0,
          items: [
            {
              documentId: 7,
              indexingStatus: 'INDEXED',
              indexedAt: '2026-10-11T11:05:00',
              indexingError: null,
            },
          ],
        }),
      ),
    };
    snackMock = { open: vi.fn() };
    dialogResults = [];
    dialogMock = {
      open: vi.fn().mockImplementation(() => ({
        afterClosed: () => of(dialogResults.shift()),
      })),
    };

    await TestBed.configureTestingModule({
      imports: [KnowledgeDocumentList],
      providers: [
        provideRouter([]),
        { provide: KnowledgeBaseService, useValue: serviceMock },
        { provide: MatDialog, useValue: dialogMock },
        { provide: MatSnackBar, useValue: snackMock },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialogMock });
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(KnowledgeDocumentList);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function lastQuery(): Record<string, unknown> {
    return serviceMock.getAdminDocuments.mock.calls.at(-1)?.[0] as Record<string, unknown>;
  }

  it('charge la liste ADMIN avec pagination et tri backend', async () => {
    await setup();

    expect(serviceMock.getAdminDocuments).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 10, sort: 'updatedAt,desc' }),
    );
    expect(fixture.nativeElement.textContent).toContain('Comment postuler ?');
    expect(fixture.nativeElement.textContent).toContain('Guide candidat');
    expect(fixture.nativeElement.textContent).toContain('Active');
    expect(fixture.nativeElement.textContent).toContain('Indexé');
    expect(fixture.nativeElement.textContent).toContain('11/10/2026 11:05');
  });

  it('réindexe une ressource et met à jour son statut local', async () => {
    await setup();
    const reindex = fixture.debugElement
      .queryAll(By.css('.row-actions button'))
      .find((button) =>
        (button.nativeElement as HTMLButtonElement).getAttribute('aria-label')?.startsWith(
          'Réindexer la ressource',
        ),
      );

    reindex?.nativeElement.click();
    fixture.detectChanges();

    expect(serviceMock.reindexDocument).toHaveBeenCalledWith(7);
    expect(snackMock.open).toHaveBeenCalledWith(
      'Ressource réindexée.',
      'Fermer',
      expect.any(Object),
    );
  });

  it('affiche l’erreur retournée par une réindexation en échec', async () => {
    await setup();
    serviceMock.reindexDocument.mockReturnValue(
      of({
        ...DOCUMENT,
        indexingStatus: 'FAILED',
        indexingError: 'Le service d’indexation est indisponible',
      }),
    );
    const reindex = fixture.debugElement
      .queryAll(By.css('.row-actions button'))
      .find((button) =>
        (button.nativeElement as HTMLButtonElement).getAttribute('aria-label')?.startsWith(
          'Réindexer la ressource',
        ),
      );

    reindex?.nativeElement.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Échec d’indexation');
    expect(snackMock.open).toHaveBeenCalledWith(
      'Le service d’indexation est indisponible',
      'Fermer',
      expect.any(Object),
    );
  });

  it('réindexe toutes les ressources après confirmation', async () => {
    await setup();
    dialogResults.push(true);
    const reindexAll = fixture.debugElement
      .queryAll(By.css('.page-header__actions button'))
      .find((button) =>
        (button.nativeElement as HTMLButtonElement).textContent?.includes(
          'Réindexer tout',
        ),
      );

    reindexAll?.nativeElement.click();
    fixture.detectChanges();

    expect(serviceMock.reindexAllDocuments).toHaveBeenCalled();
    expect(snackMock.open).toHaveBeenCalledWith(
      'Réindexation terminée : 1 ressource(s) indexée(s).',
      'Fermer',
      expect.any(Object),
    );
  });

  it('recherche par titre via le bouton Appliquer', async () => {
    await setup();
    const input = fixture.debugElement.query(By.css('input[type="search"]'))
      .nativeElement as HTMLInputElement;
    input.value = 'entretien';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.filters__apply')).nativeElement.click();
    fixture.detectChanges();

    expect(lastQuery()).toEqual(expect.objectContaining({ page: 0, search: 'entretien' }));
  });

  it('applique les filtres catégorie et statut', async () => {
    await setup();
    const selects = fixture.debugElement
      .queryAll(By.directive(MatSelect))
      .map((element) => element.componentInstance as MatSelect);

    selects[0].selectionChange.emit({ value: 'FAQ' } as MatSelectChange);
    fixture.detectChanges();
    expect(lastQuery()).toEqual(expect.objectContaining({ category: 'FAQ' }));

    selects[1].selectionChange.emit({ value: 'inactive' } as MatSelectChange);
    fixture.detectChanges();
    expect(lastQuery()).toEqual(expect.objectContaining({ active: false }));
  });

  it('réinitialise les filtres', async () => {
    await setup();
    const input = fixture.debugElement.query(By.css('input[type="search"]'))
      .nativeElement as HTMLInputElement;
    input.value = 'test';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    fixture.debugElement.query(By.css('.filters__apply')).nativeElement.click();
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.filters__head button')).nativeElement.click();
    fixture.detectChanges();

    expect(lastQuery()).toEqual(
      expect.objectContaining({ search: undefined, category: undefined, active: undefined }),
    );
  });

  it('pagine et trie côté backend', async () => {
    await setup();

    (component as unknown as { onPage(event: unknown): void }).onPage({
      pageIndex: 1,
      pageSize: 20,
      length: 30,
      previousPageIndex: 0,
    });
    expect(lastQuery()).toEqual(expect.objectContaining({ page: 1, size: 20 }));

    (component as unknown as { onSort(sort: unknown): void }).onSort({
      active: 'title',
      direction: 'asc',
    });
    expect(lastQuery()).toEqual(
      expect.objectContaining({ page: 0, sort: 'title,asc' }),
    );
  });

  it('désactive une ressource après confirmation puis recharge la liste', async () => {
    await setup();
    dialogResults.push(true);
    const callsBefore = serviceMock.getAdminDocuments.mock.calls.length;

    const toggle = fixture.debugElement
      .queryAll(By.css('.row-actions button'))
      .find((button) =>
        (button.nativeElement as HTMLButtonElement).getAttribute('aria-label')?.startsWith(
          'Désactiver la ressource',
        ),
      );
    toggle?.nativeElement.click();
    fixture.detectChanges();

    expect(serviceMock.updateDocumentStatus).toHaveBeenCalledWith(7, false);
    expect(serviceMock.getAdminDocuments.mock.calls.length).toBe(callsBefore + 1);
    expect(snackMock.open).toHaveBeenCalledWith(
      'Ressource désactivée.',
      'Fermer',
      expect.any(Object),
    );
  });

  it('supprime une ressource après confirmation', async () => {
    await setup();
    dialogResults.push(true);

    const remove = fixture.debugElement
      .queryAll(By.css('.row-actions button'))
      .find((button) =>
        (button.nativeElement as HTMLButtonElement).getAttribute('aria-label')?.startsWith(
          'Supprimer la ressource',
        ),
      );
    remove?.nativeElement.click();
    fixture.detectChanges();

    expect(serviceMock.deleteDocument).toHaveBeenCalledWith(7);
    expect(snackMock.open).toHaveBeenCalledWith(
      'Ressource supprimée.',
      'Fermer',
      expect.any(Object),
    );
  });

  it('affiche un état vide lorsque la base est vide', async () => {
    await setup([]);

    expect(fixture.nativeElement.textContent).toContain('Aucune ressource trouvée');
    expect(fixture.nativeElement.textContent).toContain(
      'La base de connaissances RH est vide',
    );
  });
});
