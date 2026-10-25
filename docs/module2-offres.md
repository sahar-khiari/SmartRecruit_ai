# Module 2 — Gestion des offres d'emploi (IA : Cohere)

Branche : `module2-offres`. Ce module **étend** les offres créées par Aziz (`OffreEmploi`, `/api/offres`)
sans casser son flux « Postuler & test IA ».

## 1. Fonctionnalités

| Bloc | Contenu |
|---|---|
| CRUD | Créer, consulter, modifier, supprimer une offre (champs enrichis : entreprise, mode de travail, expérience, salaire, date limite, mots-clés) |
| Cycle de vie | `BROUILLON → PUBLIEE → CLOTUREE → ARCHIVEE`, et `SUSPENDUE` (admin, avec motif) |
| Recherche | Multicritère : mot-clé, lieu, contrat, mode de travail, niveau, compétence, salaire minimum, pagination |
| Modération | L'admin consulte toutes les offres, voit les statistiques, suspend avec motif, réactive, supprime. **Il ne modifie jamais le contenu.** |
| IA (Cohere) | 1) Génération de la description · 2) Suggestion des compétences (embeddings + LLM) · 3) Détection des mots-clés |

### Règles de droits

| Action | Admin | Recruteur | Candidat |
|---|---|---|---|
| Voir les offres publiées | ✅ | ✅ | ✅ |
| Voir un brouillon / une offre suspendue | ✅ | ✅ (les siennes) | ❌ (404) |
| Créer / modifier le contenu | ❌ | ✅ (les siennes) | ❌ |
| Publier / clôturer / archiver | ❌ | ✅ (les siennes) | ❌ |
| Suspendre / réactiver | ✅ | ❌ | ❌ |
| Supprimer | ✅ | ✅ (les siennes) | ❌ |
| Assistant IA | ❌ | ✅ | ❌ |

## 2. Architecture

```text
Angular (features/offres)
   │  JWT
   ▼
Spring Boot  /api/offres/**  /api/admin/offres/**  /api/offres/ia/**
   │  OffreIaClient + X-AI-Service-Key (jamais exposée à Angular)
   ▼
FastAPI  app/offres/  →  /api/v1/offres/ia/{description,competences,mots-cles}
   │  anonymisation → cache → appel → validation Pydantic (+ nouvel essai)
   ▼
Cohere API v2 : chat (command-r-08-2024) + embed (embed-multilingual-v3.0)
```

## 3. Endpoints

### Spring Boot (appelés par Angular)

| Méthode | Chemin | Rôle |
|---|---|---|
| GET | `/api/offres/recherche?q&localisation&typeContrat&niveauRequis&modeTravail&competence&salaireMin&page&size` | tous (offres publiées non expirées) |
| GET | `/api/offres/{id}` | tous (brouillon visible seulement par l'auteur/admin) |
| POST | `/api/offres` (`publier: false` = brouillon) | RECRUITER |
| PUT | `/api/offres/{id}` | RECRUITER auteur |
| DELETE | `/api/offres/{id}` | auteur ou ADMIN |
| GET | `/api/offres/mes-offres/recherche?q&statut` | RECRUITER |
| PUT | `/api/offres/{id}/publier` · `/cloturer` · `/archiver` | RECRUITER auteur |
| GET | `/api/admin/offres?q&statut&recruteurId` · `/statistiques` · `/{id}` | ADMIN |
| PUT | `/api/admin/offres/{id}/suspendre` `{ "motif": "..." }` · `/reactiver` | ADMIN |
| DELETE | `/api/admin/offres/{id}` | ADMIN |
| POST | `/api/offres/ia/description` · `/competences` · `/mots-cles` | RECRUITER |

### FastAPI (appelés uniquement par Spring, clé `X-AI-Service-Key`)

`POST /api/v1/offres/ia/description`, `/competences`, `/mots-cles` — voir `http://localhost:8001/docs`.

## 4. IA : choix et fonctionnement

**Pourquoi Cohere ?** Les autres membres utilisent Ollama, Groq, Gemini et Mistral : Cohere garantit une solution
différente (critère « deux étudiants, même solution = même note »). Clé d'essai gratuite, très bon support du français
(modèle d'embedding multilingue), sortie JSON native (`response_format: json_object`), et trois briques adaptées aux
offres : génération (Command), similarité (Embed), classement (Rerank, perspective).

| Fonctionnalité | Technique | Validation / robustesse |
|---|---|---|
| Description | Chat Cohere, température 0.4, prompt `offres-v1` | Schéma `LlmDescription` (3–8 missions, 3–8 points de profil) |
| Compétences | **Hybride** : Embed (cosinus vs référentiel de ~120 compétences) → top 20 → le LLM choisit 6–10 | Repli automatique sur les embeddings si la sortie LLM est invalide |
| Mots-clés | Chat Cohere, température 0 | Comparaison avec une méthode **sans IA** (fréquence des mots) renvoyée dans `motsClesBaseline` |

Exigences du cours couvertes :

- **Aucune donnée personnelle brute** : chaque champ passe par `AnonymizationService` (emails, téléphones, URL…) avant l'appel.
- **Sortie validée** : chaque réponse JSON est validée par Pydantic ; si invalide → nouvel essai (2 max) puis erreur propre.
- **Erreurs d'API** : clé absente (503 `COHERE_KEY_MISSING`), délai dépassé (504), quota (429), service indisponible (503). Spring renvoie un message lisible, Angular l'affiche et le recruteur peut continuer à la main.
- **Optimisations** : cache LRU (même requête = 0 appel), vecteurs du référentiel calculés une seule fois, température basse pour la stabilité, prompts courts.
- **Décision humaine** : l'IA propose, le recruteur relit et modifie avant d'enregistrer.

## 5. Lancer le module

1. XAMPP : démarrer MySQL (base `smartrecruit_ai` créée automatiquement).
2. Clé Cohere : <https://dashboard.cohere.com/api-keys> → copier la clé d'essai dans `ai-service/.env` :
   `COHERE_API_KEY=...` (le fichier `.env` est ignoré par Git).
3. FastAPI :
   ```powershell
   cd ai-service
   .\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001
   ```
4. Spring Boot : `.\mvnw.cmd spring-boot:run`
5. Angular : `cd frontend` puis `npm start` (Angular 22 exige Node ≥ 22.22).

Tests : `.\mvnw.cmd test` (backend) et `.\.venv\Scripts\python.exe -m pytest tests/test_offres_ia.py` (IA, Cohere simulé).

## 6. Scénario de démo (3 min)

1. **Recruteur** → « Mes offres » → « Nouvelle offre » : titre « Développeur·se Java Spring Boot », Tunis, CDI, Junior.
2. Cliquer **Générer la description** → **Suggérer les compétences** (cliquer sur les suggestions) → **Détecter les mots-clés**.
3. « Enregistrer en brouillon », puis **Publier** depuis la liste.
4. **Candidat** → « Offres » : rechercher « Java » + Tunis + CDI → ouvrir l'offre → bouton « Postuler » (flux d'Aziz).
5. **Admin** → « Modération des offres » : statistiques, **suspendre** avec motif → le recruteur voit le motif → **réactiver**.
6. Montrer la robustesse : retirer la clé Cohere → message clair « Clé Cohere absente », le formulaire reste utilisable.

## 7. Notes d'intégration

Fichiers d'Aziz modifiés (ajouts compatibles) : `OffreEmploi`, `OffreEmploiCreateRequest`, `OffreEmploiResponse`,
`OffreEmploiServiceImpl`, `OffreEmploiRepository`, `OffreEmploiController` (rôle RECRUITER + détail filtré).
Fichiers partagés modifiés : `app.routes.ts`, `admin-sidebar`, `ai-service/app/main.py`, `ai-service/.env.example`.
Tout le reste est nouveau (`OffreGestion*`, `AdminOffreController`, `OffreIa*`, `features/offres/`, `ai-service/app/offres/`).

Changements de comportement à connaître : l'admin ne modifie plus le contenu d'une offre ; seuls les recruteurs créent
des offres ; un candidat ne voit plus une offre non publiée par son identifiant. Les anciennes pages d'Aziz
(`job-offer-list`, `my-offers`, `create-offer`) restent dans le code mais les routes pointent vers les pages du Module 2.
