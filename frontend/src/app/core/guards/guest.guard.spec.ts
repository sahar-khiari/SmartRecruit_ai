import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { User } from '../models/user.model';
import { AuthService } from '../services/auth.service';
import { guestGuard } from './guest.guard';
import { TEST_ADMIN, TEST_USER } from '../../../testing/test-utils';

function runGuard(user: User | null): boolean | UrlTree {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: AuthService,
        useValue: {
          isAuthenticated: () => user !== null,
          currentUser: () => user,
        },
      },
    ],
  });

  return TestBed.runInInjectionContext(() =>
    guestGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  ) as boolean | UrlTree;
}

describe('guestGuard', () => {
  it('autorise un visiteur non connecté à ouvrir /login', () => {
    expect(runGuard(null)).toBe(true);
  });

  it('redirige un CANDIDATE connecté vers /app/candidate', () => {
    const result = runGuard(TEST_USER);
    const router = TestBed.inject(Router);

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/app/candidate');
  });

  it('redirige un RECRUITER connecté vers /app/recruiter', () => {
    const recruiter: User = { ...TEST_USER, id: 8, role: 'RECRUITER' };
    const result = runGuard(recruiter);
    const router = TestBed.inject(Router);

    expect(router.serializeUrl(result as UrlTree)).toBe('/app/recruiter');
  });

  it('redirige un ADMIN connecté vers /admin', () => {
    const result = runGuard(TEST_ADMIN);
    const router = TestBed.inject(Router);

    expect(router.serializeUrl(result as UrlTree)).toBe('/admin');
  });
});
