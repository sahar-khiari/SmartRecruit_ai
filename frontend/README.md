# SmartRecruit AI — Frontend Angular

Interface web de SmartRecruit AI. L'application consomme **uniquement l'API Spring Boot** et ne contacte jamais directement le microservice FastAPI. Elle ne contient ni serveur mock, ni données fictives, ni réponse d'assistant simulée.

Cette phase ajoute à la base de connaissances ADMIN :

- l'affichage de l'état d'indexation vectorielle ;
- la réindexation unitaire et globale ;
- une page de **diagnostic de recherche sémantique** qui affiche des chunks et des scores.

> **Pas de LLM, pas de réponse générée.** La page de recherche sémantique est un outil ADMIN de diagnostic. Le chat utilisateur n'est pas relié à la recherche documentaire.

---

## Sommaire

- [Stack](#stack)
- [Installation](#installation)
- [Configuration](#configuration)
- [Routes](#routes)
- [Architecture](#architecture)
- [Authentification et sécurité](#authentification-et-sécurité)
- [Base de connaissances RH](#base-de-connaissances-rh)
- [Recherche sémantique ADMIN](#recherche-sémantique-admin)
- [Design system](#design-system)
- [Tests et build](#tests-et-build)
- [Validation réalisée](#validation-réalisée)
- [Limites](#limites)

---

## Stack

| Outil | Version |
|---|---|
| Angular | **22.2.3** |
| TypeScript | **6.0.x** |
| Angular Material / CDK | **22.2.3** |
| RxJS | **7.8** |
| Vitest | **5.x** |
| jsdom | **30.x** |
| Node.js | **22.x** |
| npm | **11.x** |

L'application utilise des composants standalone, le chargement paresseux des routes et les signaux Angular.

---

## Installation

```powershell
cd frontend
npm install
npm start
```

Application : `http://localhost:4200`.

Le backend Spring doit être démarré sur `http://localhost:8080` et accepter l'origine `http://localhost:4200`.

### Scripts

| Commande | Rôle |
|---|---|
| `npm start` | Serveur de développement |
| `npm run build` | Build de production |
| `npm test -- --watch=false` | Tests unitaires Vitest |
| `npm run watch` | Build de développement en continu |

---

## Configuration

`src/environments/environment.ts` et `environment.production.ts` :

```ts
export const environment = {
  production: false,
  appName: 'SmartRecruit AI',
  apiUrl: 'http://localhost:8080/api',
  defaultPageSize: 10,
  pageSizeOptions: [5, 10, 20, 50],
};
```

Aucune URL FastAPI, aucune clé `X-AI-Service-Key` et aucun secret ne figurent dans le frontend.

---

## Routes

### Public

| Route | Description |
|---|---|
| `/` | Accueil |
| `/login` | Connexion |
| `/signup` | Inscription candidat |

### ADMIN

| Route | Description |
|---|---|
| `/admin` | Tableau de bord |
| `/admin/users` | Liste des utilisateurs |
| `/admin/users/:id` | Détail utilisateur |
| `/admin/knowledge-base` | Base de connaissances RH |
| `/admin/knowledge-base/new` | Création d'une ressource |
| `/admin/knowledge-base/semantic-search` | **Diagnostic de recherche sémantique** |
| `/admin/knowledge-base/:id` | Détail d'une ressource |
| `/admin/knowledge-base/:id/edit` | Modification |
| `/admin/profile` | Profil |
| `/admin/profile/edit` | Édition du profil |
| `/admin/profile/change-password` | Changement de mot de passe |

La route `semantic-search` est déclarée **avant** la route `:id` afin d'éviter toute interprétation comme identifiant.

### CANDIDATE / RECRUITER

| Route | Rôle |
|---|---|
| `/app/candidate` | `CANDIDATE` |
| `/app/recruiter` | `RECRUITER` |
| `/app/assistant-rh` | `CANDIDATE`, `RECRUITER` |
| `/app/profile` | Profil |
| `/app/profile/edit` | Édition |
| `/app/profile/change-password` | Mot de passe |

---

## Architecture

```text
src/app/
├── core/
│   ├── guards/              # auth, guest, rôle, modifications non enregistrées
│   ├── interceptors/        # JWT, erreurs, notifications
│   ├── models/              # contrats typés, dont indexation et retrieval
│   └── services/            # API Spring, auth, navigation, notifications
├── features/
│   ├── admin/
│   │   ├── knowledge-base/
│   │   │   ├── knowledge-document-list/
│   │   │   ├── knowledge-document-details/
│   │   │   ├── knowledge-document-form/
│   │   │   └── semantic-search/
│   │   ├── user-list/
│   │   └── user-details/
│   ├── auth/
│   ├── hr-assistant/
│   ├── profile/
│   └── front-office/
├── layouts/                 # public, admin, front office
└── shared/                  # composants, pipes, styles
```

Le service `KnowledgeBaseService` centralise les appels :

- `getAdminDocuments`, `getDocumentById` ;
- `createDocument`, `updateDocument`, `updateDocumentStatus`, `deleteDocument` ;
- `reindexDocument`, `reindexAllDocuments` ;
- `getIndexingHealth` ;
- `searchSemantic`.

Les contrats `KnowledgeDocumentResponse` contiennent notamment :

```ts
indexingStatus: 'PENDING' | 'INDEXED' | 'FAILED' | 'NOT_INDEXED';
indexedAt: string | null;
indexingError: string | null;
```

---

## Authentification et sécurité

- JWT Spring Boot uniquement ; aucun appel FastAPI.
- `authGuard`, `guestGuard` et `roleGuard` protègent les routes.
- Les routes ADMIN exigent le rôle `ADMIN`.
- L'intercepteur HTTP ajoute le jeton et centralise la gestion des erreurs.
- Les erreurs d'indexation sont affichées sans exposer de secret ni de contenu FastAPI brut.
- Aucune clé interne n'est stockée dans le navigateur.

---

## Base de connaissances RH

### Liste ADMIN

- recherche plein texte, filtre par catégorie et état actif ;
- pagination et tri ;
- badge d'indexation : `INDEXED`, `FAILED`, `NOT_INDEXED`, `PENDING` ;
- date d'indexation `indexedAt` ;
- message d'erreur `indexingError` si présent ;
- action **Réindexer** par document, avec état de chargement ;
- action **Tout réindexer** avec confirmation et état de chargement dédié ;
- actualisation de la liste après réindexation.

### Détail ADMIN

- affichage de l'état d'indexation, de la date et de l'erreur ;
- action de réindexation ;
- actions d'édition, d'activation/désactivation et de suppression selon les règles existantes.

### Gestion des erreurs

- Un échec FastAPI est signalé à l'utilisateur.
- Le document MariaDB reste consultable et modifiable.
- La réindexation peut être relancée lorsque le service interne est de nouveau disponible.

---

## Recherche sémantique ADMIN

Route : `/admin/knowledge-base/semantic-search`.

Formulaire :

- question libre ;
- catégorie optionnelle ;
- nombre de résultats `topK` (1 à 10).

Résultats :

- requête originale et requête nettoyée/anonymisée ;
- titre, catégorie, identifiant du document ;
- identifiant et index du chunk ;
- version ;
- score de similarité ;
- contenu du chunk.

États gérés :

- initial ;
- chargement ;
- aucun résultat ;
- erreur `503` si le service d'indexation est indisponible ;
- erreur de validation.

La page indique explicitement qu'aucune réponse n'est générée et qu'aucun LLM n'est utilisé. Le score est une similarité cosinus, pas une probabilité.

---

## Design system

- thème Angular Material ;
- badges de statut : succès, avertissement, erreur, neutre ;
- boutons avec états `loading` et `disabled` ;
- composants partagés pour les pages, formulaires et messages ;
- responsive desktop/tablette ;
- accessibilité : libellés ARIA, focus visible, contrastes.

La classe globale `.badge--warning` complète les variantes existantes pour l'état `PENDING`.

---

## Tests et build

```powershell
cd frontend
npm test -- --watch=false
npm run build
```

Résultat de référence :

- **127 tests réussis** ;
- **22 fichiers de test** ;
- **0 échec** ;
- build de production réussi.

Les tests couvrent notamment :

- les contrats de la base de connaissances ;
- l'affichage des badges et des erreurs ;
- les actions de réindexation et leurs états de chargement ;
- la réindexation globale et la confirmation ;
- la page de recherche sémantique ;
- les états vide, erreur et chargement ;
- la génération des liens et routes ADMIN.

---

## Validation réalisée

Parcours manuel avec le backend Spring, FastAPI et MariaDB démarrés :

1. Connexion ADMIN.
2. Ouverture de `/admin/knowledge-base`.
3. Vérification du badge `INDEXED` et de la date d'indexation.
4. Réindexation d'un document depuis la liste.
5. Ouverture de `/admin/knowledge-base/semantic-search`.
6. Recherche sur une question RH.
7. Vérification de l'affichage des chunks, scores, catégories et versions.
8. Vérification de l'absence de réponse générée.

Aucun document de test n'a été conservé après la validation.

---

## Limites

- Pas de LLM et pas de réponse conversationnelle générée.
- La recherche sémantique est un diagnostic ADMIN, pas encore une fonctionnalité de chat.
- Le score affiché est une similarité, pas une probabilité.
- Aucune donnée n'est mise en cache côté frontend pour l'indexation ou la recherche.
- Le frontend dépend entièrement de la disponibilité de l'API Spring ; il n'appelle jamais FastAPI en secours.
