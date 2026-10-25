# Module 1 : Gestion des candidats (Jalel Nasr)

Branche Git : `module1-candidats`. Ce module **ajoute** des fichiers au projet SmartRecruit AI sans modifier le code des autres membres.
Les seuls fichiers partagés touchés le seront à l'intégration finale, par de simples ajouts (enregistrement des routes).

## 1. Vue d'ensemble

```text
Navigateur
   │
   ▼
Angular (4200)                 features/candidate/*, features/admin/candidates/*
   │  JWT (Authorization: Bearer)
   ▼
Spring Boot (8080)             /api/candidates/**, /api/admin/candidates/**
   │  ├── MariaDB : candidate_profiles, candidate_skills, candidate_cvs, cv_analyses
   │  ├── Stockage des PDF : hors du dépôt (${user.home}/smartrecruit/cvs)
   │  └── CvAnalysisClient ── X-AI-Service-Key ──┐
   ▼                                             ▼
FastAPI ai-service (8001)      POST /api/v1/cv/analyze
   └── Pipeline cv_analysis
        1. Lecture du PDF            pdfplumber                      (pas d'IA)
        2. Anonymisation             regex + spaCy NER               (IA locale)
        3. Extraction + résumé       Gemini Flash → JSON validé      (LLM, secours Flash-Lite)
        4. Niveau d'expérience       règle métier sur les années     (pas d'IA)
        5. Normalisation             référentiel + rapidfuzz         (pas d'IA)
```

Règles :
- Angular n'appelle jamais FastAPI ni Gemini. Les clés API restent dans `ai-service/.env`.
- Aucune donnée personnelle brute n'est envoyée à Gemini : l'anonymisation passe toujours avant.
- Toute sortie d'un LLM est validée (Pydantic côté Python, puis contrôle côté Spring) avant d'être enregistrée.
- Le candidat vérifie et valide le résultat : l'IA propose, l'humain décide.

## 2. Modèles IA (différents de ceux des autres modules)

| Usage | Modèle | Pourquoi |
|---|---|---|
| Détection des noms et lieux | spaCy `fr_core_news_md` (local) | NER : un nom n'a pas de format fixe, une regex ne suffit pas |
| Extraction du CV en JSON + résumé | Gemini Flash (`gemini-3.5-flash`) | sortie JSON structurée native, clé gratuite |
| Secours si le modèle principal échoue | Gemini Flash-Lite (`gemini-3.5-flash-lite`) | quota séparé, environ 1 s ; gestion des erreurs d'API |
| Niveau d'expérience | règle métier (0-2 / 3-5 / 6+ ans) | déterministe, identique à l'enum Java |

Mistral était prévu au départ ; abandonné car son offre n'était pas gratuite. La comparaison de modèles porte sur Flash contre Flash-Lite.

Autres modules, pour comparaison : Assistant RH = Ollama `qwen3:4b` + MiniLM + ChromaDB ; Tests techniques = Groq `llama-3.3-70b`.

## 3. Arborescence

### 3.1 Back-end Spring Boot (`src/main/java/com/smartrecruit/`)

Organisation par couche, comme le reste du back-end. Tous les fichiers sont préfixés `Candidate` ou `Cv`.

```text
entity/
  ├── CandidateProfile.java            ✅ table candidate_profiles (1 profil par compte CANDIDATE)
  ├── CandidateSkill.java              ✅ table candidate_skills
  ├── ExperienceLevel.java             ✅ JUNIOR / INTERMEDIATE / SENIOR + règle des années
  ├── SkillSource.java                 ✅ MANUAL / AI
  ├── CandidateCv.java                 ✅ étape 2 : métadonnées du PDF déposé
  ├── CvAnalysis.java                  ✅ étape 6 : résultat et traçabilité d'une analyse IA
  └── CvAnalysisStatus.java            ✅ étape 6 : PENDING / COMPLETED / FAILED
repository/
  ├── CandidateProfileRepository.java  ✅ recherche par nom, titre, compétence, niveau, chargement groupé
  ├── CandidateCvRepository.java       ✅ étape 2
  └── CvAnalysisRepository.java        ✅ étape 6
dto/request/
  ├── CandidateProfileUpdateRequest.java  ✅ étape 1
  ├── CandidateSkillsUpdateRequest.java   ✅ étape 1
  └── AdminCandidateCreateRequest.java    ✅ étape 1
dto/response/
  ├── CandidateProfileResponse.java    ✅ étape 1
  ├── CandidateSkillResponse.java      ✅ étape 1
  ├── CandidateSummaryResponse.java    ✅ étape 1
  ├── CandidateCvResponse.java         ✅ étape 2
  └── CvAnalysisResponse.java          ✅ étape 6
dto/ai/
  ├── AiCvAnalysisRequest.java         ✅ étape 6 : contrat Spring → FastAPI
  └── AiCvAnalysisResponse.java        ✅ étape 6
mapper/
  ├── CandidateProfileMapper.java      ✅ étape 1
  └── CvAnalysisMapper.java            ✅ étape 6
service/                               (interfaces)          service/impl/  (implémentations)
  ├── CandidateService.java            ✅ étape 1             ├── CandidateServiceImpl.java   ✅
  ├── CandidateCvService.java          ✅ étape 2             ├── CandidateCvServiceImpl.java ✅
  └── CvAnalysisService.java           ✅ étape 6             └── CvAnalysisServiceImpl.java  ✅
controller/
  ├── CandidateController.java         ✅ étape 1 : /api/candidates (me, liste, détail)
  ├── AdminCandidateController.java    ✅ étape 1 : /api/admin/candidates (créer, modifier, supprimer)
  └── CandidateCvController.java       ✅ étape 2 : /api/candidates/me/cv
integration/ai/
  ├── CvAnalysisClient.java            ✅ étape 6 : client séparé, le client existant n'est pas modifié
  └── RestCvAnalysisClient.java        ✅ étape 6
exception/
  ├── CandidateNotFoundException.java  ✅ étape 1 (404, via le gestionnaire global existant)
  ├── CvStorageException.java          ✅ étape 2 (500, sans détail technique)
  ├── InvalidCvFileException.java      ✅ étape 2 (400 : vide / pas un PDF, 413 : plus de 5 Mo)
  ├── CandidateCvExceptionHandler.java ✅ étape 2 : gestionnaire séparé, le GlobalExceptionHandler commun n'est pas modifié
  └── CvAnalysisException.java         ✅ étape 6

src/test/java/com/smartrecruit/
  ├── CandidateIntegrationTest.java    ✅ étape 1
  ├── CandidateCvIntegrationTest.java  ✅ étape 2
  └── CvAnalysisIntegrationTest.java   ✅ étape 6 (FastAPI simulé)
```

### 3.2 Service IA FastAPI (`ai-service/`)

Même organisation que le module Tests techniques (`api/` → `v1/endpoints/` → `v1/schemas/` → `coreAgents/Agents/`).

```text
app/api/cv_analysis.py                 ✅ étape 3 : routeur (préfixe /api/v1)
app/v1/endpoints/cv_analysis.py        ✅ étape 3 : POST /cv/analyze, protégé par X-AI-Service-Key
app/v1/schemas/cv_analysis.py          ✅ étape 4 : schémas Pydantic (validation des JSON des LLM)
app/coreAgents/Agents/cv_analysis/
  ├── __init__.py                      ✅
  ├── constants.py                     ✅ limites (taille du texte, nombre de compétences...)
  ├── pdf_reader.py                    ✅ étape 3 : PDF → texte
  ├── anonymizer.py                    ✅ étape 3 : regex + spaCy NER
  ├── settings.py                      ✅ étape 4 : GEMINI_API_KEY, modèles, délais
  ├── prompts.py                       ✅ étape 4 : prompts versionnés (cv-extract-v1, cv-summary-v1...)
  ├── extractor.py                     ✅ étape 4 : nouvel essai puis bascule sur le secours
  ├── profiler.py                      ✅ étape 4 : niveau par règle métier
  ├── skill_normalizer.py              ✅ étape 5 : EXACT, VERSION, FUZZY (rapidfuzz ≥ 88), sinon UNKNOWN conservée
  ├── pipeline.py                      ✅ enchaîne les 5 étapes et mesure les durées et mesure les durées
  ├── llm/
  │   ├── __init__.py                  ✅
  │   ├── base.py                      ✅ interface commune + erreurs (timeout, quota, clé absente)
  │   └── gemini_client.py             ✅ étape 4 (API REST via httpx)
  └── data/skills_referential.json     ✅ étape 5 : 125 compétences IT, 10 catégories, variantes
tests/test_cv_analysis/                ✅ dossier ; tests ⬜ étapes 3 à 5 (LLM simulés)
data/cv_samples/                       ✅ dossier ; 10 à 15 CV fictifs ⬜ (jamais de vrais CV)
scripts/compare_cv_models.py           ⬜ comparaison Gemini Flash / Flash-Lite (JSON valides, latence, justesse)
docs/prompts-jalel.md                  ⬜ journal de prompts (documentation individuelle)
```

### 3.3 Front-end Angular (`frontend/src/app/`)

```text
core/models/candidate.model.ts         ⬜ étape 7 : types alignés sur les DTO Spring
core/services/candidate.service.ts     ⬜ étape 7 : appels /api/candidates/**
features/candidate/
  ├── my-profile/                      ⬜ profil du candidat (consultation, modification)
  ├── cv-upload/                       ⬜ écran 1 de la maquette : dépôt du PDF
  ├── cv-analysis/                     ⬜ écrans 2 et 3 : pipeline en direct + cartes de résultat
  ├── profile-review/                  ⬜ écran 4 : vérification et validation par le candidat
  └── candidate-directory/             ⬜ écran 5 : liste des candidats (recruteur)
features/admin/candidates/
  ├── candidate-list/                  ⬜ liste admin (recherche, suppression)
  └── candidate-form/                  ⬜ création / modification par l'admin
```

## 4. Base de données (MariaDB, base `techmatch`)

Tables créées automatiquement par Hibernate (`ddl-auto=update`). Les tables existantes ne sont pas modifiées.

```text
users (existante) 1 ──── 0..1 candidate_profiles 1 ──── * candidate_skills
                                     │
                                     ├── 1 ──── * candidate_cvs   1 ──── * cv_analyses
```

| Table | Colonnes principales |
|---|---|
| `candidate_profiles` | id, user_id (unique, FK cascade), headline, phone, city, github_url, linkedin_url, summary, experience_level, years_of_experience, created_at, updated_at |
| `candidate_skills` | id, profile_id (FK cascade), name (unique par profil), source |
| `candidate_cvs` | étape 2 : id, profile_id, original_filename, storage_key, size_bytes, uploaded_at |
| `cv_analyses` | étape 6 : id, cv_id, status, résultat JSON, modèles utilisés, durées, tokens, version des prompts, erreur |

## 5. API REST (contrat)

L'identifiant d'un candidat est l'id de son compte utilisateur, réutilisable par les autres modules.

| Méthode | Chemin | Accès | Étape |
|---|---|---|---|
| GET | `/api/candidates/me` | CANDIDATE | 1 |
| PUT | `/api/candidates/me` | CANDIDATE | 1 |
| PUT | `/api/candidates/me/skills` | CANDIDATE | 1 |
| GET | `/api/candidates?search=&level=&page=` | RECRUITER, ADMIN | 1 |
| GET | `/api/candidates/{id}` | RECRUITER, ADMIN | 1 |
| POST | `/api/admin/candidates` | ADMIN | 1 |
| PUT | `/api/admin/candidates/{id}` | ADMIN | 1 |
| PUT | `/api/admin/candidates/{id}/skills` | ADMIN | 1 |
| DELETE | `/api/admin/candidates/{id}` | ADMIN | 1 |
| POST | `/api/candidates/me/cv` (multipart, PDF ≤ 5 Mo) | CANDIDATE | 2 |
| GET | `/api/candidates/me/cv` | CANDIDATE | 2 |
| GET | `/api/candidates/{id}/cv/file` | RECRUITER, ADMIN, propriétaire | 2 |
| POST | `/api/candidates/me/cv/analyze` | CANDIDATE | 6 |
| GET | `/api/candidates/me/cv/analysis` | CANDIDATE | 6 |
| POST | `/api/candidates/me/cv/analysis/apply` | CANDIDATE | 6 |
| — | Historique des candidatures | dépend du module Offres (Dali) | intégration |

API interne FastAPI (appelée uniquement par Spring) :

| Méthode | Chemin | Clé requise |
|---|---|---|
| POST | `/api/v1/cv/analyze` | oui (`X-AI-Service-Key`) |

## 6. Étapes

| # | Étape | Couche |
|---|---|---|
| 0 | Environnement, base `techmatch`, branche `module1-candidats` | ✅ |
| 1 | Profil + compétences : entités, CRUD, sécurité par rôle, tests | Spring ✅ |
| 2 | Upload du CV PDF (validation type/taille, stockage) | Spring ✅ |
| 3 | Lecture du PDF + anonymisation (regex + spaCy) | Python ✅ |
| 4 | Gemini (extraction + résumé, secours Flash-Lite), niveau par règle, JSON validé, erreurs d'API | Python ✅ |
| 5 | Normalisation des compétences (référentiel + rapidfuzz) | Python ✅ |
| 6 | Branchement Spring ↔ FastAPI, traçabilité des analyses | Spring ✅ |
| 7 | Écrans Angular (selon la maquette) | Angular |
| 8 | Tests, journal de prompts, comparaison des modèles, pull request | tous |

## 7. Intégration finale (avec Dali, Aziz et Sahar)

Ajouts prévus dans des fichiers partagés, uniquement à ce moment-là et après accord :
- `ai-service/app/main.py` : une ligne `include_router(cv_analysis.router)` ;
- `frontend/src/app/app.routes.ts` : les routes des écrans du module ;
- `frontend/.../candidate-home` : activer les raccourcis « Mon profil » et « Mon CV » ;
- `ai-service/requirements.txt` : `pdfplumber`, `spacy`, `preshed==3.0.10`, `python-multipart`, `rapidfuzz` ;
- `ai-service/.env.example` : `GEMINI_API_KEY` (sans valeur) ;
- `README.md` : section du Module 1.

Point à aligner avec Dali : l'historique des candidatures relie un candidat (`users.id`) à une offre.

## 8. Observations à reporter dans le journal de prompts (étape 8)

- Prompt `cv-extract-v1`, CV fictif avec fautes (2026-10-25) : Gemini Flash a omis « Kubernete » (compétence
  mal orthographiée) et « Francais » (sans cédille). Piste `cv-extract-v2` : demander de lister toutes les
  compétences telles qu'écrites, même mal orthographiées (la normalisation corrige ensuite), puis comparer v1 / v2.
- Gemini Flash-Lite a omis « API REST » sur le CV fictif de base, que Flash a trouvé.
- spaCy ne reconnaît pas toutes les villes tunisiennes (« Ariana » non masquée).
