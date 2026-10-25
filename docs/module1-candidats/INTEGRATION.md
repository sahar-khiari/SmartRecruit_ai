# Intégration du Module 1 dans le projet d'équipe

Le Module 1 est développé dans un dossier indépendant (`C:\Users\DELL\SmartRecruit_module1`).
Le jour où le projet d'équipe est prêt, il est recopié dans `C:\Users\DELL\SmartRecruit_ai`, sur la branche `module1-candidats`.

## Ce qui est recopié, et ce qui ne l'est pas

| Recopié (73 fichiers, tous nouveaux) | Jamais recopié (adapté pour le projet indépendant) |
|---|---|
| Spring : `Candidate*`, `AdminCandidate*`, `Cv*`, `ExperienceLevel`, `SkillSource`, `InvalidCvFileException`, `RestCvAnalysisClient`, `AiCvAnalysisResponse` + tests | `GlobalExceptionHandler` (handlers IA/RH retirés), `application.properties` (base `techmatch`) |
| IA : `app/coreAgents/Agents/cv_analysis/`, `app/api/cv_analysis.py`, `app/v1/endpoints/cv_analysis.py`, `app/v1/schemas/cv_analysis.py`, `tests/test_cv_analysis/` | `ai-service/app/main.py`, copies de `app/core/` et `app/services/` (identiques à celles de l'équipe) |
| `docs/module1-candidats/` | tout `frontend/` (menus « Bientôt » du projet indépendant) |

Fichiers partagés : **uniquement des lignes ajoutées**, dans un bloc marqué « Module 1 (Jalel) » :

| Fichier | Ajout |
|---|---|
| `src/main/resources/application.properties` | stockage des CV, taille max, délai de l'analyse |
| `src/test/resources/application-test.properties` | dossier temporaire des CV de test |
| `ai-service/requirements.txt` | pdfplumber, spaCy, preshed 3.0.10, rapidfuzz, python-multipart, fpdf2 |
| `ai-service/.env.example` | `GEMINI_API_KEY=` (sans valeur) |
| `ai-service/app/main.py` | **à la main** : `cv_analysis` dans l'import et `include_router(cv_analysis.router)` |

Le code des autres membres n'est jamais modifié. `SecurityConfig` n'est pas touché : les rôles sont vérifiés par `@PreAuthorize`.

## Le jour J

```powershell
# 1. Récupérer la dernière version de l'équipe
cd C:\Users\DELL\SmartRecruit_ai
git checkout module1-candidats
git pull origin main

# 2. Simuler, puis copier
cd C:\Users\DELL\SmartRecruit_module1
ai-service\.venv\Scripts\python.exe scripts\integrer_module1.py
ai-service\.venv\Scripts\python.exe scripts\integrer_module1.py --apply

# 3. Les 2 lignes de main.py, les dépendances, la clé Gemini dans ai-service\.env (le script les rappelle)

# 4. Tous les tests, les leurs et les miens
cd C:\Users\DELL\SmartRecruit_ai
.\mvnw.cmd clean test
cd ai-service; .\.venv\Scripts\python.exe -m pytest

# 5. Vérifier puis commiter (pas de push sans accord de l'équipe)
git status
git diff
```

Le script refuse d'écraser un fichier existant et différent (affiché « CONFLIT »), ne supprime rien et ne commite rien.

## Points d'attention

- **Base** : l'équipe utilise `smartrecruit_ai`. Les tables `candidate_profiles`, `candidate_skills`, `candidate_cvs` et `cv_analyses` y sont créées au premier démarrage.
- **Suppression d'un candidat** : la table `candidatures` (Module Offres) n'a pas de suppression en cascade. Supprimer un candidat qui a postulé renvoie 409. À voir avec Dali.
- **Historique des candidatures** : à brancher sur l'entité `Candidature` du Module Offres.
- **Tests Python** : les fixtures du module sont dans `tests/test_cv_analysis/conftest.py` (le `tests/conftest.py` commun n'est pas touché) ; lancer avec `python -m pytest`.
- **Réseau de l'école** : pypi.org peut être bloqué. Les paquets se téléchargent alors depuis `https://files.pythonhosted.org/packages/cp311/<initiale>/<nom>/<fichier>.whl`, puis `pip install --no-deps <fichier>`.
- **Front-end (étape 7)** : se fait directement dans le front de l'équipe, après l'intégration.
