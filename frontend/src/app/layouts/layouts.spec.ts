import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { Component, signal, Type, WritableSignal } from '@angular/core';
import { User } from '../core/models/user.model';
import { AuthService } from '../core/services/auth.service';
import { TEST_ADMIN, TEST_USER } from '../../testing/test-utils';
import { AdminLayout } from './admin-layout/admin-layout';
import { AdminSidebar } from './admin-layout/components/admin-sidebar/admin-sidebar';
import { FrontOfficeLayout } from './front-office-layout/front-office-layout';
import { FrontOfficeTopbar } from './front-office-layout/components/front-office-topbar/front-office-topbar';

@Component({ template: '' })
class TestRouteComponent {}

describe('Séparation des layouts par rôle', () => {
  let currentUser: WritableSignal<User | null>;

  async function configure(user: User, routes: Parameters<typeof provideRouter>[0] = []): Promise<void> {
    currentUser = signal<User | null>(user);

    await TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        {
          provide: AuthService,
          useValue: {
            currentUser: () => currentUser(),
            displayName: () =>
              `${currentUser()?.firstName ?? ''} ${currentUser()?.lastName ?? ''}`,
            logout: vi.fn(),
          },
        },
      ],
    }).compileComponents();
  }

  function create<T>(component: Type<T>): ComponentFixture<T> {
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    return fixture;
  }

  it('rend la sidebar et la topbar admin dans AdminLayout', async () => {
    await configure(TEST_ADMIN);
    const fixture = create(AdminLayout);

    expect(fixture.debugElement.query(By.css('app-admin-sidebar'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('app-admin-topbar'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('app-front-office-topbar'))).toBeNull();
  });

  it('rend la topbar et le footer front-office sans sidebar admin', async () => {
    await configure(TEST_USER);
    const fixture = create(FrontOfficeLayout);

    expect(fixture.debugElement.query(By.css('app-front-office-topbar'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('app-front-office-footer'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('app-admin-sidebar'))).toBeNull();
    expect(fixture.debugElement.query(By.css('mat-sidenav'))).toBeNull();
  });

  it('affiche Utilisateurs dans la sidebar administrateur', async () => {
    await configure(TEST_ADMIN);
    const fixture = create(AdminSidebar);

    expect(fixture.nativeElement.textContent).toContain('Tableau de bord');
    expect(fixture.nativeElement.textContent).toContain('Utilisateurs');
    expect(fixture.nativeElement.textContent).toContain('Administration');
  });

  it('affiche la base de connaissances RH uniquement dans la sidebar ADMIN', async () => {
    await configure(TEST_ADMIN);
    const adminFixture = create(AdminSidebar);
    const adminText = adminFixture.nativeElement.textContent as string;
    const knowledgeLink = adminFixture.debugElement
      .queryAll(By.css('a'))
      .find((element) =>
        (element.nativeElement.textContent as string).includes('Base de connaissances RH'),
      );

    expect(adminText).toContain('Base de connaissances RH');
    expect(knowledgeLink?.nativeElement.getAttribute('href')).toContain(
      '/admin/knowledge-base',
    );
    expect(adminText).not.toContain('Assistant RH');
  });

  it('affiche le menu candidat avec les modules à venir', async () => {
    await configure(TEST_USER);
    const fixture = create(FrontOfficeTopbar);
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Accueil');
    expect(text).toContain('Offres');
    expect(text).toContain('Mes candidatures');
    expect(text).toContain('Mes entretiens');
    expect(text).toContain('Assistant RH');
    expect(text).toContain('Mon profil');
    expect(text).toContain('Bientôt');
    expect(text).not.toContain('Gestion des utilisateurs');
  });

  it('affiche le menu recruteur avec les modules à venir', async () => {
    await configure({ ...TEST_USER, id: 21, role: 'RECRUITER' });
    const fixture = create(FrontOfficeTopbar);
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Accueil');
    expect(text).toContain('Mes offres');
    expect(text).toContain('Candidatures');
    expect(text).toContain('Entretiens');
    expect(text).toContain('Assistant RH');
    expect(text).toContain('Mon profil');
    expect(text).toContain('Bientôt');
    expect(text).not.toContain('Gestion des utilisateurs');
  });

  it('active le lien Assistant RH pour un candidat', async () => {
    await configure(TEST_USER, [
      { path: 'app/assistant-rh', component: TestRouteComponent },
    ]);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/app/assistant-rh');

    const fixture = create(FrontOfficeTopbar);
    await fixture.whenStable();
    fixture.detectChanges();

    const link = fixture.debugElement
      .queryAll(By.css('.fo-nav__link'))
      .find((element) =>
        (element.nativeElement.textContent as string).includes('Assistant RH'),
      );
    const anchor = link?.nativeElement as HTMLAnchorElement;

    expect(anchor.getAttribute('href')).toContain('/app/assistant-rh');
    expect(anchor.classList.contains('fo-nav__link--active')).toBe(true);
    expect(anchor.classList.contains('fo-nav__link--soon')).toBe(false);
  });

  it('active le lien Assistant RH pour un recruteur', async () => {
    await configure({ ...TEST_USER, id: 21, role: 'RECRUITER' }, [
      { path: 'app/assistant-rh', component: TestRouteComponent },
    ]);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/app/assistant-rh');

    const fixture = create(FrontOfficeTopbar);
    await fixture.whenStable();
    fixture.detectChanges();

    const link = fixture.debugElement
      .queryAll(By.css('.fo-nav__link'))
      .find((element) =>
        (element.nativeElement.textContent as string).includes('Assistant RH'),
      );

    expect(link?.nativeElement.classList.contains('fo-nav__link--active')).toBe(true);
  });

  it('propose un bouton de menu mobile dans le front-office', async () => {
    await configure(TEST_USER);
    const fixture = create(FrontOfficeTopbar);

    expect(fixture.debugElement.query(By.css('.fo-topbar__menu-button'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('app-admin-sidebar'))).toBeNull();
  });
});
