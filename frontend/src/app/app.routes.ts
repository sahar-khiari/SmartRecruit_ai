import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { roleGuard } from './core/guards/role.guard';
import { unsavedChangesGuard } from './core/guards/unsaved-changes.guard';
import { RoleNavigationService } from './core/services/role-navigation.service';
import { AdminLayout } from './layouts/admin-layout/admin-layout';
import { FrontOfficeLayout } from './layouts/front-office-layout/front-office-layout';
import { PublicLayout } from './layouts/public-layout/public-layout';

/** Redirection centralisée vers l'accueil du rôle connecté. */
const redirectToRoleHome = (): string =>
  inject(RoleNavigationService).homePathForCurrentUser();

const redirectToRoleProfile = (): string =>
  inject(RoleNavigationService).profilePathForCurrentUser('view');

const redirectToRoleProfileEdit = (): string =>
  inject(RoleNavigationService).profilePathForCurrentUser('edit');

const redirectToRolePassword = (): string =>
  inject(RoleNavigationService).profilePathForCurrentUser('change-password');

export const routes: Routes = [
  {
    path: '',
    component: PublicLayout,
    children: [
      {
        path: '',
        title: 'SmartRecruit AI — Recrutement intelligent',
        loadComponent: () => import('./features/home/home').then((module) => module.Home),
      },
      {
        path: 'login',
        title: 'Connexion | SmartRecruit AI',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/login/login').then((module) => module.Login),
      },
      {
        path: 'signup',
        title: 'Créer un compte | SmartRecruit AI',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/signup/signup').then((module) => module.Signup),
      },
    ],
  },
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Tableau de bord administratif | SmartRecruit AI',
        loadComponent: () =>
          import('./features/admin/dashboard/admin-dashboard').then(
            (module) => module.AdminDashboard,
          ),
      },
      {
        path: 'dashboard',
        pathMatch: 'full',
        redirectTo: '/admin',
      },
      {
        path: 'users',
        title: 'Gestion des utilisateurs | SmartRecruit AI',
        loadComponent: () =>
          import('./features/admin/user-list/user-list').then((module) => module.UserList),
      },
      {
        path: 'users/:id',
        title: 'Détails de l’utilisateur | SmartRecruit AI',
        loadComponent: () =>
          import('./features/admin/user-details/user-details').then(
            (module) => module.UserDetails,
          ),
      },
      {
        path: 'knowledge-base',
        pathMatch: 'full',
        title: 'Base de connaissances RH | SmartRecruit AI',
        loadComponent: () =>
          import(
            './features/admin/knowledge-base/knowledge-document-list/knowledge-document-list'
          ).then((module) => module.KnowledgeDocumentList),
      },
      {
        path: 'tests-techniques',
        title: 'Tests techniques & Evaluation IA | SmartRecruit AI',
        loadComponent: () =>
          import('./features/tech-assessment/tech-assessment').then(
            (module) => module.TechAssessment,
          ),
      },
      {
        path: 'knowledge-base/new',
        title: 'Ajouter une ressource RH | SmartRecruit AI',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import(
            './features/admin/knowledge-base/knowledge-document-form/knowledge-document-form'
          ).then((module) => module.KnowledgeDocumentForm),
      },
      {
        path: 'knowledge-base/semantic-search',
        title: 'Recherche sémantique RH | SmartRecruit AI',
        loadComponent: () =>
          import(
            './features/admin/knowledge-base/semantic-search/semantic-search'
          ).then((module) => module.SemanticSearch),
      },
      {
        path: 'knowledge-base/:id/edit',
        title: 'Modifier une ressource RH | SmartRecruit AI',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import(
            './features/admin/knowledge-base/knowledge-document-form/knowledge-document-form'
          ).then((module) => module.KnowledgeDocumentForm),
      },
      {
        path: 'knowledge-base/:id',
        title: 'Détail de la ressource RH | SmartRecruit AI',
        loadComponent: () =>
          import(
            './features/admin/knowledge-base/knowledge-document-details/knowledge-document-details'
          ).then((module) => module.KnowledgeDocumentDetails),
      },
      {
        path: 'profile',
        title: 'Mon profil administrateur | SmartRecruit AI',
        loadComponent: () =>
          import('./features/profile/profile-view/profile-view').then(
            (module) => module.ProfileView,
          ),
      },
      {
        path: 'profile/edit',
        title: 'Modifier mon profil | SmartRecruit AI',
        loadComponent: () =>
          import('./features/profile/profile-edit/profile-edit').then(
            (module) => module.ProfileEdit,
          ),
      },
      {
        path: 'profile/change-password',
        title: 'Changer le mot de passe | SmartRecruit AI',
        loadComponent: () =>
          import('./features/profile/change-password/change-password').then(
            (module) => module.ChangePassword,
          ),
      },
    ],
  },
  {
    path: 'app',
    component: FrontOfficeLayout,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CANDIDATE', 'RECRUITER'] },
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: redirectToRoleHome,
      },
      {
        path: 'candidate',
        title: 'Espace candidat | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['CANDIDATE'] },
        loadComponent: () =>
          import('./features/front-office/candidate/candidate-home').then(
            (module) => module.CandidateHome,
          ),
      },
      {
        path: 'recruiter',
        title: 'Espace recruteur | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['RECRUITER'] },
        loadComponent: () =>
          import('./features/front-office/recruiter/recruiter-home').then(
            (module) => module.RecruiterHome,
          ),
      },
      {
        path: 'assistant-rh',
        title: 'Assistant RH | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['CANDIDATE', 'RECRUITER'] },
        loadComponent: () =>
          import('./features/hr-assistant/assistant-page/assistant-page').then(
            (module) => module.AssistantPage,
          ),
      },
      {
        path: 'tests-techniques',
        title: 'Tests techniques & Evaluation IA | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['CANDIDATE', 'RECRUITER'] },
        loadComponent: () =>
          import('./features/tech-assessment/tech-assessment').then(
            (module) => module.TechAssessment,
          ),
      },
      {
        path: 'offres',
        title: 'Offres d\'emploi | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['CANDIDATE', 'RECRUITER'] },
        loadComponent: () =>
          import('./features/job-offers/job-offer-list/job-offer-list').then(
            (module) => module.JobOfferList,
          ),
      },
      {
        path: 'offres/:id/postuler',
        title: 'Postuler & Test IA | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['CANDIDATE'] },
        loadComponent: () =>
          import('./features/job-offers/apply-with-test/apply-with-test').then(
            (module) => module.ApplyWithTest,
          ),
      },
      {
        path: 'recruteur/offres',
        title: 'Mes offres | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['RECRUITER', 'ADMIN'] },
        loadComponent: () =>
          import('./features/recruiter/my-offers/my-offers').then(
            (module) => module.MyOffers,
          ),
      },
      {
        path: 'recruteur/offres/new',
        title: 'Créer une offre | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['RECRUITER', 'ADMIN'] },
        loadComponent: () =>
          import('./features/recruiter/create-offer/create-offer').then(
            (module) => module.CreateOffer,
          ),
      },
      {
        path: 'recruteur/offres/:id/edit',
        title: 'Modifier une offre | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['RECRUITER', 'ADMIN'] },
        loadComponent: () =>
          import('./features/recruiter/create-offer/create-offer').then(
            (module) => module.CreateOffer,
          ),
      },
      {
        path: 'recruteur/offres/:id/candidatures',
        title: 'Candidatures reçues | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['RECRUITER', 'ADMIN'] },
        loadComponent: () =>
          import('./features/recruiter/offer-applications/offer-applications').then(
            (module) => module.OfferApplications,
          ),
      },
      {
        path: 'recruteur/offres/:offreId/matching',
        title: 'Classement Matching | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['RECRUITER', 'ADMIN'] },
        loadComponent: () =>
          import('./features/matching/matching-ranking/matching-ranking').then(
            (module) => module.MatchingRanking,
          ),
      },
      {
        path: 'recruteur/offres/:offreId/criteres',
        title: 'Critères de matching | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['RECRUITER', 'ADMIN'] },
        loadComponent: () =>
          import('./features/matching/matching-criteria/matching-criteria').then(
            (module) => module.MatchingCriteria,
          ),
      },
      {
        path: 'matching/profile',
        title: 'Mon profil CV & Matching | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['CANDIDATE'] },
        loadComponent: () =>
          import('./features/matching/matching-profile/matching-profile').then(
            (module) => module.MatchingProfile,
          ),
      },
      {
        path: 'matching/recommandations',
        title: 'Offres recommandées | SmartRecruit AI',
        canActivate: [roleGuard],
        data: { roles: ['CANDIDATE'] },
        loadComponent: () =>
          import(
            './features/matching/matching-recommendations/matching-recommendations'
          ).then((module) => module.MatchingRecommendations),
      },
      {
        path: 'profile',
        title: 'Mon profil | SmartRecruit AI',
        loadComponent: () =>
          import('./features/profile/profile-view/profile-view').then(
            (module) => module.ProfileView,
          ),
      },
      {
        path: 'profile/edit',
        title: 'Modifier mon profil | SmartRecruit AI',
        loadComponent: () =>
          import('./features/profile/profile-edit/profile-edit').then(
            (module) => module.ProfileEdit,
          ),
      },
      {
        path: 'profile/change-password',
        title: 'Changer le mot de passe | SmartRecruit AI',
        loadComponent: () =>
          import('./features/profile/change-password/change-password').then(
            (module) => module.ChangePassword,
          ),
      },
    ],
  },
  {
    path: 'access-denied',
    title: 'Accès refusé | SmartRecruit AI',
    loadComponent: () =>
      import('./shared/components/access-denied/access-denied').then(
        (module) => module.AccessDenied,
      ),
  },
  // Compatibilité avec les anciennes routes de l'application.
  { path: 'dashboard', pathMatch: 'full', redirectTo: redirectToRoleHome },
  { path: 'profile', pathMatch: 'full', redirectTo: redirectToRoleProfile },
  { path: 'profile/edit', pathMatch: 'full', redirectTo: redirectToRoleProfileEdit },
  {
    path: 'profile/change-password',
    pathMatch: 'full',
    redirectTo: redirectToRolePassword,
  },
  {
    path: '**',
    title: 'Page introuvable | SmartRecruit AI',
    loadComponent: () =>
      import('./features/not-found/not-found').then((module) => module.NotFound),
  },
];
