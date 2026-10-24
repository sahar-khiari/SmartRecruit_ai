import { TestBed } from '@angular/core/testing';
import { User } from '../models/user.model';
import { AuthService } from './auth.service';
import { RoleNavigationService } from './role-navigation.service';
import { TEST_ADMIN, TEST_USER } from '../../../testing/test-utils';

function createService(user: User | null): RoleNavigationService {
  TestBed.configureTestingModule({
    providers: [
      {
        provide: AuthService,
        useValue: { currentUser: () => user },
      },
    ],
  });

  return TestBed.inject(RoleNavigationService);
}

describe('RoleNavigationService', () => {
  it('associe chaque rôle à son espace d’accueil', () => {
    const service = createService(null);

    expect(service.homePath('ADMIN')).toBe('/admin');
    expect(service.homePath('CANDIDATE')).toBe('/app/candidate');
    expect(service.homePath('RECRUITER')).toBe('/app/recruiter');
  });

  it('associe chaque rôle à ses routes de profil', () => {
    const service = createService(null);

    expect(service.profilePath('ADMIN')).toBe('/admin/profile');
    expect(service.profilePath('ADMIN', 'edit')).toBe('/admin/profile/edit');
    expect(service.profilePath('ADMIN', 'change-password')).toBe(
      '/admin/profile/change-password',
    );

    expect(service.profilePath('CANDIDATE')).toBe('/app/profile');
    expect(service.profilePath('CANDIDATE', 'edit')).toBe('/app/profile/edit');
    expect(service.profilePath('RECRUITER')).toBe('/app/profile');
    expect(service.profilePath('RECRUITER', 'change-password')).toBe(
      '/app/profile/change-password',
    );
  });

  it('renvoie l’accueil de l’ADMIN connecté', () => {
    const service = createService(TEST_ADMIN);

    expect(service.currentRole()).toBe('ADMIN');
    expect(service.homePathForCurrentUser()).toBe('/admin');
    expect(service.profilePathForCurrentUser()).toBe('/admin/profile');
  });

  it('renvoie l’accueil du CANDIDATE connecté', () => {
    const service = createService(TEST_USER);

    expect(service.currentRole()).toBe('CANDIDATE');
    expect(service.homePathForCurrentUser()).toBe('/app/candidate');
    expect(service.profilePathForCurrentUser('edit')).toBe('/app/profile/edit');
  });

  it('renvoie l’accueil du RECRUITER connecté', () => {
    const recruiter: User = { ...TEST_USER, id: 12, role: 'RECRUITER' };
    const service = createService(recruiter);

    expect(service.currentRole()).toBe('RECRUITER');
    expect(service.homePathForCurrentUser()).toBe('/app/recruiter');
    expect(service.profilePathForCurrentUser('change-password')).toBe(
      '/app/profile/change-password',
    );
  });

  it('redirige vers /login sans session', () => {
    const service = createService(null);

    expect(service.homePathForCurrentUser()).toBe('/login');
    expect(service.profilePathForCurrentUser()).toBe('/login');
    expect(service.isAdminSpace()).toBe(false);
  });
});
