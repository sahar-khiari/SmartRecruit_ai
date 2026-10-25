import { Route } from '@angular/router';
import { routes } from './app.routes';
import { AdminLayout } from './layouts/admin-layout/admin-layout';
import { FrontOfficeLayout } from './layouts/front-office-layout/front-office-layout';

function topLevelRoute(path: string): Route {
  const route = routes.find((candidate) => candidate.path === path);
  if (!route) {
    throw new Error(`Route ${path} introuvable`);
  }
  return route;
}

function childRoute(parent: Route, path: string): Route {
  const route = parent.children?.find((candidate) => candidate.path === path);
  if (!route) {
    throw new Error(`Route enfant ${path} introuvable`);
  }
  return route;
}

describe('app routes', () => {
  it('réserve AdminLayout au rôle ADMIN', () => {
    const admin = topLevelRoute('admin');

    expect(admin.component).toBe(AdminLayout);
    expect(admin.data?.['roles']).toEqual(['ADMIN']);
    expect(admin.canActivate?.length).toBe(2);
  });

  it('utilise FrontOfficeLayout pour CANDIDATE et RECRUITER', () => {
    const app = topLevelRoute('app');

    expect(app.component).toBe(FrontOfficeLayout);
    expect(app.data?.['roles']).toEqual(['CANDIDATE', 'RECRUITER']);
  });

  it('expose des accueils distincts candidat et recruteur', () => {
    const app = topLevelRoute('app');

    expect(childRoute(app, 'candidate').data?.['roles']).toEqual(['CANDIDATE']);
    expect(childRoute(app, 'recruiter').data?.['roles']).toEqual(['RECRUITER']);
  });

  it('réserve l’Assistant RH au front-office CANDIDATE et RECRUITER', () => {
    const app = topLevelRoute('app');
    const assistant = childRoute(app, 'assistant-rh');

    expect(assistant.data?.['roles']).toEqual(['CANDIDATE', 'RECRUITER']);
    expect(assistant.canActivate).toBeDefined();
    expect(assistant.loadComponent).toBeDefined();
    expect(app.component).toBe(FrontOfficeLayout);
  });

  it('expose les routes ADMIN de la base de connaissances RH', () => {
    const admin = topLevelRoute('admin');

    expect(childRoute(admin, 'knowledge-base').loadComponent).toBeDefined();
    expect(childRoute(admin, 'knowledge-base/new').canDeactivate).toHaveLength(1);
    expect(childRoute(admin, 'knowledge-base/:id/edit').canDeactivate).toHaveLength(1);
    expect(childRoute(admin, 'knowledge-base/semantic-search').loadComponent).toBeDefined();
    expect(childRoute(admin, 'knowledge-base/:id').loadComponent).toBeDefined();
    expect(admin.component).toBe(AdminLayout);
    expect(admin.data?.['roles']).toEqual(['ADMIN']);
  });

  it('rend le profil accessible dans les deux espaces', () => {
    const admin = topLevelRoute('admin');
    const app = topLevelRoute('app');

    expect(childRoute(admin, 'profile')).toBeTruthy();
    expect(childRoute(app, 'profile')).toBeTruthy();
    expect(childRoute(app, 'profile/edit')).toBeTruthy();
    expect(childRoute(app, 'profile/change-password')).toBeTruthy();
  });

  it('conserve les redirections des anciennes routes', () => {
    expect(topLevelRoute('dashboard').redirectTo).toBeDefined();
    expect(topLevelRoute('profile').redirectTo).toBeDefined();
    expect(topLevelRoute('profile/edit').redirectTo).toBeDefined();
    expect(topLevelRoute('profile/change-password').redirectTo).toBeDefined();
  });

  it('ne place pas AccessDenied sous le layout administrateur', () => {
    const denied = topLevelRoute('access-denied');

    expect(denied.component).toBeUndefined();
    expect(denied.loadComponent).toBeDefined();
  });
});
