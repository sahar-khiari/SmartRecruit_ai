# Module 2 — Gestion des offres d'emploi (IA : Cohere)

Branche : `module2-offres`. Ce module **enrichit les offres et les pages d'Aziz** (`OffreEmploi`, `/api/offres`,
`job-offer-list`, `my-offers`, `create-offer`) sans casser son flux « Postuler & test IA ».
Aucune modification de l'espace admin.

## 1. Fonctionnalités

| Bloc | Contenu |
|---|---|
| CRUD | Créer, consulter, modifier, supprimer une offre (+ mots-clés) |
| Cycle de vie | `BROUILLON → PUBLIEE → CLOTUREE → ARCHIVEE` (remplace l'ancien interrupteur actif/inactif, `active` reste synchronisé) |
| Recherche | Multicritère : mot-clé, lieu, contrat, niveau, compétence, pagination |
| IA (Cohere) | 1) Génération de la description · 2) Suggestion des compétences (embeddings + LLM) · 3) Détection des mots-clés |

### Règles de droits

| Action | Admin | Recruteur | Candidat |
|---|---|---|---|
| Voir les offres publiées | ✅ | ✅ | ✅ |
| Voir un brouillon / une offre clôturée | ✅ | ✅ (les siennes) | ❌ (404) |
| Créer / modifier le contenu | ❌ | ✅ (les siennes) | ❌ |
| Publier / clôturer / archiver | ❌ | ✅ (les siennes) | ❌ |
| Supprimer | ✅ | ✅ (les siennes) | ❌ |
| Assistant IA | ❌ | ✅ | ❌ |

## 2. Où sont les fonctionnalités dans l'interface (pages d'Aziz enrichies)

| Page | Ajouts du Module 2 |
|---|---|
| `job-offer-list` (`/app/offres`) | Barre de recherche multicritère |
| `my-offers` (`/app/recruteur/offres`) | Recherche + filtre par statut, badge de statut, boutons Publier / Clôturer / Archiver |
| `create-offer` (`/app/recruteur/offres/new`, `/:id/edit`) | 3 boutons IA, compétences et mots-clés en chips, « Enregistrer en brouillon » / « Publier » |

Inchangées : `apply-with-test`, `offer-applications` (Aziz). Les routes et menus ne sont pas modifiés.

## 3. Architecture

```text
Angular (pages d'Aziz + OffreGestionService)
   │  JWT
   ▼
Spring Boot  /api/offres/**  /api/offres/ia/**
   │  OffreIaClient + X-AI-Service-Key (jamais exposée à Angular)
   ▼
FastAPI  app/offres/  →  /api/v1/offres/ia/{description,competences,mots-cles}
   │  anonymisation → cache → appel → validation Pydantic (+ nouvel essai)
   ▼
Cohere API v2 : chat (command-r-08-2024) + embed (embed-multilingual-v3.0)
```

## 4. Endpoints

### Spring Boot (appelés par Angular)

| Méthode | Chemin | Rôle |
|---|---|---|
| GET | `/api/offres/recherche?q&localisation&typeContrat&niveauRequis&competence&page&size` | tous (offres publiées) |
| GET | `/api/offres/{id}` | tous (offre non publiée visible seulement par l'auteur/admin) |
| POST | `/api/offres` (`publier: false` = brouillon, absent = publication directe) | RECRUITER |
| PUT | `/api/offres/{id}` | RECRUITER auteur |
| DELETE | `/api/offres/{id}` | auteur ou ADMIN |
| GET | `/api/offres/mes-offres/recherche?q&statut` | RECRUITER |
| PUT | `/api/offres/{id}/publier` · `/cloturer` · `/archiver` | RECRUITER auteur |
| POST | `/api/offres/ia/description` · `/competences` · `/mots-cles` | RECRUITER |

### FastAPI (appelés uniquement par Spring, clé `X-AI-Service-Key`)

`POST /api/v1/offres/ia/description`, `/competences`, `/mots-cles` — voir `http://localhost:8001/docs`.

## 5. IA : choix et fonctionnement

**Pourquoi Cohere ?** Les autres membres utilisent Ollama, Groq, Gemini et Mistral : Cohere garantit une solution
différente (critère « deux étudiants, même solution = même note »). Clé d'essai gratuite, très bon support du français
(modèle d'embedding multilingue), sortie JSON native (`response_format: json_object`).

| Fonctionnalité | Technique | Validation / robustesse |
|---|---|---|
| Description | Chat Cohere, température 0.4, prompt `offres-v1` | Schéma `LlmDescription` (3–8 missions, 3–8 points de profil) |
| Compétences | **Hybride** : Embed (cosinus vs référentiel de ~120 compétences) → top 20 → le LLM choisit 6–10 | Repli automatique sur les embeddings si la sortie LLM est invalide |
| Mots-clés | Chat Cohere, température 0 | Comparaison avec une méthode **sans IA** (fréquence des mots) renvoyée dans `motsClesBaseline` |

Exigences du cours couvertes :

- **Aucune donnée personnelle brute** : chaque champ passe par `AnonymizationService` avant l'appel.
- **Sortie validée** : chaque réponse JSON est validée par Pydantic ; si invalide → nouvel essai (2 max) puis erreur propre.
- **Erreurs d'API** : clé absente (503 `COHERE_KEY_MISSING`), délai dépassé (504), quota (429), service indisponible (503). Angular affiche le message et le recruteur peut continuer à la main.
- **Optimisations** : cache LRU, vecteurs du référentiel calculés une seule fois, température basse, prompts courts.
- **Décision humaine** : l'IA propose, le recruteur relit et modifie avant d'enregistrer.

## 6. Lancer le module

1. XAMPP : démarrer MySQL (base `smartrecruit_ai`).
2. Clé Cohere : <https://dashboard.cohere.com/api-keys> → `COHERE_API_KEY=...` dans `ai-service/.env` (ignoré par Git).
3. FastAPI : `cd ai-service` puis `.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001`
4. Spring Boot : `.\mvnw.cmd spring-boot:run`
5. Angular : `cd frontend` puis `npm start` (Angular 22 exige Node ≥ 22.22).

Tests : `.\mvnw.cmd test` (backend) et `.\.venv\Scripts\python.exe -m pytest tests/test_offres_ia.py` (IA, Cohere simulé).

## 7. Scénario de démo (3 min)

1. **Recruteur** → « Mes offres » → « Créer une offre » : « Développeur·se Java Spring Boot », Tunis, CDI, Junior.
2. **Générer avec l'IA** → **Suggérer les compétences** (cliquer sur les suggestions) → **Détecter les mots-clés**.
3. « Enregistrer en brouillon », puis **Publier** depuis « Mes offres » (filtre par statut).
4. **Candidat** → « Offres » : rechercher « Java » + Tunis + CDI → « Postuler & passer le test IA » (flux d'Aziz).
5. **Recruteur** → **Clôturer** l'offre → elle disparaît pour le candidat.
6. Robustesse : retirer la clé Cohere → message clair « Clé Cohere absente », le formulaire reste utilisable.

## 8. Notes d'intégration

Fichiers d'Aziz enrichis : backend `OffreEmploi`, `OffreEmploiCreateRequest`, `OffreEmploiResponse`,
`OffreEmploiServiceImpl`, `OffreEmploiRepository`, `OffreEmploiController` ; frontend `job-offer-list`, `my-offers`,
`create-offer`. Fichiers partagés : `ai-service/app/main.py` (2 lignes), `ai-service/.env.example`.
Nouveaux fichiers : `OffreGestion*`, `OffreIa*`, `OffreEmploiMapper`, `OffreEmploiSpecifications`, `StatutOffre`,
`OffreStatutMigration`, `offre-gestion.model.ts`, `offre-gestion.service.ts`, `ai-service/app/offres/`.
Non modifiés : routes, menus, espace admin.

Changements de comportement : l'admin ne modifie plus le contenu d'une offre ; seuls les recruteurs créent des offres ;
un candidat ne voit plus une offre non publiée par son identifiant ; l'interrupteur actif/inactif est remplacé par les statuts.
