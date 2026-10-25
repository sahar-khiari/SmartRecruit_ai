import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { KnowledgeDocumentResponse } from '../../../../core/models/hr/knowledge-document.model';
import { KnowledgeBaseService } from '../../../../core/services/knowledge-base.service';
import { KnowledgeDocumentDetails } from './knowledge-document-details';

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

describe('KnowledgeDocumentDetails', () => {
  let fixture: ComponentFixture<KnowledgeDocumentDetails>;
  let serviceMock: {
    getDocumentById: ReturnType<typeof vi.fn>;
    updateDocumentStatus: ReturnType<typeof vi.fn>;
    deleteDocument: ReturnType<typeof vi.fn>;
    reindexDocument: ReturnType<typeof vi.fn>;
  };
  let snackMock: { open: ReturnType<typeof vi.fn> };
  let dialogResults: unknown[];
  let dialogMock: { open: ReturnType<typeof vi.fn> };
  let router: Router;

  async function setup(
    loadResult: KnowledgeDocumentResponse | HttpErrorResponse = DOCUMENT,
  ): Promise<void> {
    serviceMock = {
      getDocumentById:
        loadResult instanceof HttpErrorResponse
          ? vi.fn().mockReturnValue(throwError(() => loadResult))
          : vi.fn().mockReturnValue(of(loadResult)),
      updateDocumentStatus: vi.fn().mockReturnValue(of({ ...DOCUMENT, active: false })),
      deleteDocument: vi.fn().mockReturnValue(of(void 0)),
      reindexDocument: vi.fn().mockReturnValue(of(DOCUMENT)),
    };
    snackMock = { open: vi.fn() };
    dialogResults = [];
    dialogMock = {
      open: vi.fn().mockImplementation(() => ({
        afterClosed: () => of(dialogResults.shift()),
      })),
    };

    await TestBed.configureTestingModule({
      imports: [KnowledgeDocumentDetails],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: { get: (key: string) => (key === 'id' ? '7' : null) },
            },
          },
        },
        { provide: KnowledgeBaseService, useValue: serviceMock },
        { provide: MatDialog, useValue: dialogMock },
        { provide: MatSnackBar, useValue: snackMock },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialogMock });
    await TestBed.compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(KnowledgeDocumentDetails);
    fixture.detectChanges();
  }

  function clickButton(label: string): void {
    const button = fixture.debugElement
      .queryAll(By.css('button'))
      .find((element) =>
        (element.nativeElement as HTMLButtonElement).textContent?.includes(label),
      );
    expect(button).toBeTruthy();
    button?.nativeElement.click();
    fixture.detectChanges();
  }

  it('affiche le contenu et les métadonnées de la ressource', async () => {
    await setup();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Comment postuler ?');
    expect(text).toContain('Guide candidat');
    expect(text).toContain('Connectez-vous puis cliquez sur Postuler.');
    expect(text).toContain('Administrateur #1');
    expect(text).toContain('Indexé');
    expect(text).toContain('11/10/2026 à 11:05');
  });

  it('réindexe la ressource depuis le détail', async () => {
    await setup();

    clickButton('Réindexer la ressource');

    expect(serviceMock.reindexDocument).toHaveBeenCalledWith(7);
    expect(snackMock.open).toHaveBeenCalledWith(
      'Ressource réindexée.',
      'Fermer',
      expect.any(Object),
    );
  });

  it('désactive la ressource après confirmation', async () => {
    await setup();
    dialogResults.push(true);

    clickButton('Désactiver la ressource');

    expect(serviceMock.updateDocumentStatus).toHaveBeenCalledWith(7, false);
    expect(snackMock.open).toHaveBeenCalledWith(
      'Ressource désactivée.',
      'Fermer',
      expect.any(Object),
    );
  });

  it('supprime la ressource puis retourne à la liste', async () => {
    await setup();
    dialogResults.push(true);

    clickButton('Supprimer la ressource');

    expect(serviceMock.deleteDocument).toHaveBeenCalledWith(7);
    expect(router.navigate).toHaveBeenCalledWith(['/admin/knowledge-base']);
  });

  it('redirige vers la liste lorsqu’une ressource est introuvable (404)', async () => {
    await setup(new HttpErrorResponse({ status: 404 }));

    expect(router.navigate).toHaveBeenCalledWith(['/admin/knowledge-base']);
    expect(snackMock.open).toHaveBeenCalledWith(
      "Cette ressource n'existe plus. Retour à la liste.",
      'Fermer',
      expect.any(Object),
    );
  });
});
