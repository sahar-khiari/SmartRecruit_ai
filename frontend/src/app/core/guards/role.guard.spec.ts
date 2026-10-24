import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, UrlTree } from '@angular/router';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';
import { User } from '../models/user.model';
import { TEST_ADMIN, TEST_USER } from '../../../testing/test-utils';

function routeWithRoles(roles: string[]): ActivatedRouteSnapshot {
  return {
    data: { roles },
    pathFromRoot: [{ url: [] }],
  } as unknown as ActivatedRouteSnapshot;
}

function configure(user: User | null): void {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: AuthService,
        useValue: {
          isAuthenticated: () => user !== null,
          currentUser: () => user,
          hasRole: (roles: string | readonly string[]) => {
            if (!user) {
              return false;
            }
            const required = Array.isArray(roles) ? roles : [roles];
            return required.includes(user.role);
          },
        },
      },
    ],
  });
}

function execute(roles: string[], user: User | null): boolean | UrlTree {
  configure(user);
  return TestBed.runInInjectionContext(() =>
    roleGuard(routeWithRoles(roles), {} as never),
  ) as boolean | UrlTree;
}

function serialized(result: boolean | UrlTree): string {
  return TestBed.inject(Router).serializeUrl(result as UrlTree);
}

describe('roleGuard', () => {
  it('autorise un ADMIN sur une route réservée aux administrateurs', () => {
    expect(execute(['ADMIN'], TEST_ADMIN)).toBe(true);
  });

  it('autorise un CANDIDATE sur une route front-office', () => {
    expect(execute(['CANDIDATE', 'RECRUITER'], TEST_USER)).toBe(true);
  });

  it('redirige un CANDIDATE vers /access-denied sur les routes ADMIN', () => {
    expect(serialized(execute(['ADMIN'], TEST_USER))).toBe('/access-denied');
  });

  it('redirige un RECRUITER vers /access-denied sur les routes ADMIN', () => {
    const recruiter: User = { ...TEST_USER, id: 9, role: 'RECRUITER' };
    expect(serialized(execute(['ADMIN'], recruiter))).toBe('/access-denied');
  });

  it('redirige un ADMIN hors du front-office vers /admin', () => {
    expect(serialized(execute(['CANDIDATE', 'RECRUITER'], TEST_ADMIN))).toBe('/admin');
  });

  it('redirige un utilisateur non connecté vers /login', () => {
    const result = execute(['ADMIN'], null);

    expect(result).toBeInstanceOf(UrlTree);
    expect(serialized(result).startsWith('/login')).toBe(true);
  });
});
