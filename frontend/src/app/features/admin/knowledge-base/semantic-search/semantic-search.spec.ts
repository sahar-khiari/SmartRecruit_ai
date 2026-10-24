import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RetrievalSearchResponse } from '../../../../core/models/hr/knowledge-document.model';
import { KnowledgeBaseService } from '../../../../core/services/knowledge-base.service';
import { SemanticSearch } from './semantic-search';

const RESPONSE: RetrievalSearchResponse = {
  query: 'Comment se déroule un recrutement ?',
  sanitizedQuery: 'Comment se déroule un recrutement ?',
  results: [
    {
      documentId: 12,
      chunkId: 'document-12-v1-chunk-0',
      chunkIndex: 0,
      title: 'Processus de recrutement',
      category: 'RECRUITMENT_PROCEDURE',
      content: 'Le processus comporte un entretien téléphonique puis un entretien final.',
      score: 0.87,
      version: 1,
    },
  ],
  totalResults: 1,
  modelName: 'paraphrase-multilingual-MiniLM-L12-v2',
  durationMs: 42,
};

describe('SemanticSearch', () => {
  let fixture: ComponentFixture<SemanticSearch>;
  let serviceMock: { searchSemantic: ReturnType<typeof vi.fn> };

  async function setup(
    result: RetrievalSearchResponse | HttpErrorResponse = RESPONSE,
  ): Promise<void> {
    serviceMock = {
      searchSemantic:
        result instanceof HttpErrorResponse
          ? vi.fn().mockReturnValue(throwError(() => result))
          : vi.fn().mockReturnValue(of(result)),
    };

    await TestBed.configureTestingModule({
      imports: [SemanticSearch],
      providers: [
        provideRouter([]),
        { provide: KnowledgeBaseService, useValue: serviceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SemanticSearch);
    fixture.detectChanges();
  }

  function form(): FormGroup {
    return (fixture.componentInstance as unknown as { form: FormGroup }).form;
  }

  function submit(): void {
    fixture.debugElement
      .query(By.css('form'))
      .nativeElement.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('affiche un diagnostic sans réponse générée au repos', async () => {
    await setup();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Recherche sémantique RH');
    expect(text).toContain('aucune réponse n’est générée');
    expect(text).toContain('RAG local sans LLM');
    expect(serviceMock.searchSemantic).not.toHaveBeenCalled();
  });

  it('transmet la question, la catégorie et topK, puis affiche les chunks', async () => {
    await setup();
    form().patchValue({
      query: '  Comment se déroule un recrutement ?  ',
      category: 'RECRUITMENT_PROCEDURE',
      topK: 3,
    });

    submit();

    expect(serviceMock.searchSemantic).toHaveBeenCalledWith({
      query: 'Comment se déroule un recrutement ?',
      topK: 3,
      categories: ['RECRUITMENT_PROCEDURE'],
    });
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('1 chunk trouvé');
    expect(text).toContain('Processus de recrutement');
    expect(text).toContain('Le processus comporte un entretien téléphonique');
    expect(text).toContain('0.870');
    expect(text).toContain('87 %');
    expect(text).toContain('document-12-v1-chunk-0');
  });

  it('affiche la requête anonymisée lorsqu’elle diffère', async () => {
    await setup({
      ...RESPONSE,
      query: 'Contactez a@b.fr pour le recrutement',
      sanitizedQuery: 'Contactez [EMAIL] pour le recrutement',
    });
    form().patchValue({ query: 'Contactez a@b.fr pour le recrutement' });

    submit();

    expect(fixture.nativeElement.textContent).toContain('Requête anonymisée avant embedding');
    expect(fixture.nativeElement.textContent).toContain('Contactez [EMAIL]');
  });

  it('affiche un état vide lorsque le seuil ne retourne aucun chunk', async () => {
    await setup({ ...RESPONSE, results: [], totalResults: 0 });
    form().patchValue({ query: 'question sans résultat' });

    submit();

    expect(fixture.nativeElement.textContent).toContain('Aucun chunk pertinent');
  });

  it('signale une erreur de service sans afficher de faux résultat', async () => {
    await setup(new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable' }));
    form().patchValue({ query: 'recrutement' });

    submit();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('La recherche sémantique est indisponible');
    expect(text).not.toContain('chunk trouvé');
  });

  it('refuse une question vide sans appeler le backend', async () => {
    await setup();
    form().patchValue({ query: '   ' });

    submit();

    expect(serviceMock.searchSemantic).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('La question est obligatoire');
  });
});
