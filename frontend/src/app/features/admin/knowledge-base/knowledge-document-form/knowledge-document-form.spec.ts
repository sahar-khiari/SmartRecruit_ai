import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { KnowledgeDocumentResponse } from '../../../../core/models/hr/knowledge-document.model';
import { KnowledgeBaseService } from '../../../../core/services/knowledge-base.service';
import { KnowledgeDocumentForm } from './knowledge-document-form';

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

describe('KnowledgeDocumentForm', () => {
  let fixture: ComponentFixture<KnowledgeDocumentForm>;
  let component: KnowledgeDocumentForm;
  let serviceMock: {
    getDocumentById: ReturnType<typeof vi.fn>;
    createDocument: ReturnType<typeof vi.fn>;
    updateDocument: ReturnType<typeof vi.fn>;
  };
  let snackMock: { open: ReturnType<typeof vi.fn> };
  let router: Router;

  async function setup(
    id: string | null = null,
    loadResult: KnowledgeDocumentResponse | HttpErrorResponse = DOCUMENT,
  ): Promise<void> {
    serviceMock = {
      getDocumentById:
        loadResult instanceof HttpErrorResponse
          ? vi.fn().mockReturnValue(throwError(() => loadResult))
          : vi.fn().mockReturnValue(of(loadResult)),
      createDocument: vi.fn().mockReturnValue(of(DOCUMENT)),
      updateDocument: vi.fn().mockReturnValue(of(DOCUMENT)),
    };
    snackMock = { open: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [KnowledgeDocumentForm],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? id : null),
              },
            },
          },
        },
        { provide: KnowledgeBaseService, useValue: serviceMock },
        { provide: MatSnackBar, useValue: snackMock },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(KnowledgeDocumentForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function form(): FormGroup {
    return (component as unknown as { form: FormGroup }).form;
  }

  function submit(): void {
    fixture.debugElement
      .query(By.css('form'))
      .nativeElement.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('refuse un formulaire vide sans appeler l’API', async () => {
    await setup();

    submit();

    expect(serviceMock.createDocument).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Le titre est obligatoire');
    expect(fixture.nativeElement.textContent).toContain('La catégorie est obligatoire');
    expect(fixture.nativeElement.textContent).toContain('Le contenu est obligatoire');
  });

  it('refuse un titre et un contenu composés uniquement d’espaces', async () => {
    await setup();
    form().patchValue({
      title: '   ',
      category: 'FAQ',
      content: '   ',
    });
    fixture.detectChanges();

    submit();

    expect(serviceMock.createDocument).not.toHaveBeenCalled();
  });

  it('refuse un titre ou un contenu dépassant la limite backend', async () => {
    await setup();
    form().patchValue({
      title: 'a'.repeat(201),
      category: 'FAQ',
      content: 'a'.repeat(100_001),
    });
    fixture.detectChanges();

    submit();

    expect(serviceMock.createDocument).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'Le titre ne doit pas dépasser 200 caractères',
    );
  });

  it('crée une ressource avec des valeurs nettoyées', async () => {
    await setup();
    form().patchValue({
      title: '  Nouvelle ressource  ',
      category: 'FAQ',
      content: '  Contenu de la ressource.  ',
    });
    fixture.detectChanges();

    submit();

    expect(serviceMock.createDocument).toHaveBeenCalledWith({
      title: 'Nouvelle ressource',
      category: 'FAQ',
      content: 'Contenu de la ressource.',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/admin/knowledge-base', 7]);
    expect(snackMock.open).toHaveBeenCalledWith(
      'Ressource créée.',
      'Fermer',
      expect.any(Object),
    );
  });

  it('charge une ressource existante et la met à jour', async () => {
    await setup('7');

    expect(serviceMock.getDocumentById).toHaveBeenCalledWith(7);
    expect(form().getRawValue()).toEqual({
      title: DOCUMENT.title,
      category: DOCUMENT.category,
      content: DOCUMENT.content,
    });

    form().patchValue({ title: 'Titre actualisé' });
    fixture.detectChanges();
    submit();

    expect(serviceMock.updateDocument).toHaveBeenCalledWith(7, {
      title: 'Titre actualisé',
      category: DOCUMENT.category,
      content: DOCUMENT.content,
    });
    expect(router.navigate).toHaveBeenCalledWith(['/admin/knowledge-base', 7]);
  });

  it('signale les modifications non enregistrées au guard', async () => {
    await setup('7');
    expect(component.hasUnsavedChanges()).toBe(false);

    form().patchValue({ title: 'Modification locale' });
    form().markAsDirty();

    expect(component.hasUnsavedChanges()).toBe(true);
  });

  it('retourne à la liste lorsqu’une ressource à modifier est introuvable (404)', async () => {
    await setup('7', new HttpErrorResponse({ status: 404 }));

    expect(router.navigate).toHaveBeenCalledWith(['/admin/knowledge-base']);
    expect(snackMock.open).toHaveBeenCalledWith(
      "Cette ressource n'existe plus. Retour à la liste.",
      'Fermer',
      expect.any(Object),
    );
  });
});
