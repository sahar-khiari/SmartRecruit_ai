import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MessageComposer } from './message-composer';

describe('MessageComposer', () => {
  let fixture: ComponentFixture<MessageComposer>;
  let component: MessageComposer;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessageComposer],
    }).compileComponents();

    fixture = TestBed.createComponent(MessageComposer);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function textarea(): HTMLTextAreaElement {
    return fixture.debugElement.query(By.css('textarea')).nativeElement as HTMLTextAreaElement;
  }

  function form(): HTMLFormElement {
    return fixture.debugElement.query(By.css('form')).nativeElement as HTMLFormElement;
  }

  function type(value: string): void {
    const field = textarea();
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('refuse un message vide et affiche une erreur', () => {
    const emitted = vi.fn();
    component.send.subscribe(emitted);

    form().dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(emitted).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('ne peut pas être vide');
  });

  it('refuse un message composé uniquement d’espaces', () => {
    const emitted = vi.fn();
    component.send.subscribe(emitted);

    type('    ');
    form().dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(emitted).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('ne peut pas être vide');
  });

  it('envoie le contenu nettoyé et se vide après confirmation du parent', () => {
    const emitted = vi.fn();
    component.send.subscribe(emitted);

    type('  Comment postuler à une offre ?  ');
    form().dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(emitted).toHaveBeenCalledWith('Comment postuler à une offre ?');

    fixture.componentRef.setInput('resetToken', 1);
    fixture.detectChanges();

    expect(textarea().value).toBe('');
  });

  it('permet l’envoi avec Ctrl + Entrée', () => {
    const emitted = vi.fn();
    component.send.subscribe(emitted);

    type('Question RH');
    textarea().dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, bubbles: true }),
    );
    fixture.detectChanges();

    expect(emitted).toHaveBeenCalledWith('Question RH');
  });

  it('désactive la saisie et le bouton pendant l’envoi', () => {
    type('Question RH');
    fixture.componentRef.setInput('sending', true);
    fixture.detectChanges();

    expect(textarea().disabled).toBe(true);
    expect(
      (fixture.debugElement.query(By.css('button[type="submit"]'))
        .nativeElement as HTMLButtonElement).disabled,
    ).toBe(true);
  });
});
