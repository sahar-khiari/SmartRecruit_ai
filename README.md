# SmartRecruit AI — Plateforme Intelligente de Recrutement & Évaluation Technique

Application universitaire **SmartRecruit AI** développée dans le cadre du cours *« IA for Software Engineering »* (ESPRIT - 5SAE, 2026-2027). Ce projet regroupe une suite complète de microservices et d'agents IA pour assister les candidats et les recruteurs tout au long du cycle de recrutement et de montée en compétences :

- un backend **Spring Boot / MariaDB** : utilisateurs, authentification JWT, rôles, profil, assistant RH, traçabilité des générations et base de connaissances ;
- un frontend **Angular** : espaces candidat, recruteur et administration, assistant RH avec sources RAG, et espace interactif de tests techniques et feuille de route IA (`/app/tests-techniques`) ;
- un microservice interne **FastAPI (`ai-service`)** intégrant deux modules majeurs :
  - **Module 1 — Assistant RH & RAG Local (Sahar)** : nettoyage, anonymisation, découpage, embeddings locaux, recherche sémantique dans **ChromaDB** et génération RAG locale via **Ollama** (`qwen3:4b`), 100% local sans fuite de données ;
  - **Module 4 — Tests techniques adaptatifs, Évaluation automatique & Feuille de route IA (Aziz)** : système multi-agents IA générant des tests sur-mesure (QCM + code), notation automatique avec feedback détaillé, et plan de formation sur 8 semaines via inférence accélérée LLM (**Groq API / Llama-3.3-70b-versatile** et fallback local Ollama).

> **Périmètre actuel**
>
> - Le chat candidat/recruteur est relié à la base de connaissances RH.
> - La question est anonymisée avant retrieval, prompt et appel Ollama.
> - Le modèle reçoit uniquement les chunks pertinents et doit produire un JSON strict.
> - Les sources sont reconstruites depuis le retrieval ; un chunk ID inventé est refusé.
> - En cas de contexte insuffisant, Ollama n'est pas appelé et une réponse de repli contrôlée est enregistrée.
> - MariaDB reste la source de vérité. ChromaDB ne contient qu'un index dérivé, reconstructible.
> - Aucun fournisseur d'IA externe n'est utilisé.

---

## 1. Architecture

```text
Navigateur
   │
   ├───────────────────────────────┐
   ▼ (Port 4200)                   ▼ (Port 4200)
Angular : Espace RH           Angular : Tests Techniques (/app/tests-techniques)
   │  JWT                          │
   ▼                               │ Requêtes Tests & Roadmap
Spring Boot (localhost:8080)       │
   ├── MariaDB (localhost:3306)    │
   │      users, conversations,    │
   │      messages, sources, AiRun │
   └── client HTTP interne         │
          │                        │
          └───────────┬────────────┘
                      ▼
               FastAPI (127.0.0.1:8001)
     ┌────────────────┴────────────────┐
     ▼                                 ▼
[MODULE 1 : RAG RH Local]     [MODULE 4 : Multi-Agents Tests Techniques]
 ├── nettoyage & anonymisation ├── TestGeneratorAgent (génération adaptative)
 ├── chunks déterministes      ├── EvaluationAgent (scoring de code)
 ├── embeddings locaux MiniLM  ├── RoadmapAgent (formation sur 8 semaines)
 ├── ChromaDB persistant       ├── Prompts versionnés (v1 / v2 / v3)
 ├── retrieval filtré          ├── Persistance SQLite (techmatch.db)
 └── client Ollama local       └── Moteur LLM Hybride (Groq API / Ollama)
        │                             │
        ▼                             ▼
   Ollama (11434) → qwen3:4b     Groq Cloud / Ollama local
```

Règles d'architecture :

- Angular ne contacte **jamais** FastAPI ni Ollama et ne reçoit **jamais** `X-AI-Service-Key`.
- Seul Spring Boot possède la clé interne et appelle le service FastAPI.
- FastAPI est le seul composant qui appelle Ollama.
- La suppression ou la mise à jour d'un document passe toujours par MariaDB puis par l'index.
- En cas d'échec FastAPI, le document MariaDB est conservé et son état d'indexation passe à `FAILED`.
- En cas d'échec FastAPI/Ollama sur `/ask`, le message USER reste enregistré, aucun faux message ASSISTANT n'est créé et le `AiRun` passe à `FAILED`.
- Aucun appel à un fournisseur d'IA externe n'est effectué.

---

## 2. Prérequis

| Outil | Version vérifiée |
|---|---|
| JDK | **17.0.20** (Temurin) |
| Node.js | **22.x** |
| npm | **11.x** |
| Angular CLI | **22.2.3** |
| Python | **3.11.9** (`py -3.11`) |
| MariaDB | XAMPP, port `3306` |
| Modèle d'embedding | `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` |
| ChromaDB | `1.0.21` |
| FastAPI | `0.115.14` |
| sentence-transformers | `5.1.2` |
| PyTorch CPU | `2.7.1+cpu` |
| Ollama | local, port `11434` |
| Modèle LLM | `qwen3:4b` |
| Groq API | clé API gratuite (recommandé pour Module 4) |
| SQLite | inclus dans Python (base locale Module 4) |

Le premier calcul d'embedding télécharge le modèle (environ 470 Mo) dans le cache Hugging Face. Cette étape peut prendre plusieurs minutes ; elle n'a lieu qu'une fois.

---

## 3. Configuration

### 3.1 Backend Spring Boot

Copier le fichier d'exemple :

```powershell
Copy-Item .env.example .env
```

Variables principales :

| Variable | Valeur locale par défaut | Rôle |
|---|---|---|
| `DB_URL` | `jdbc:mariadb://localhost:3306/smartrecruit_ai` | Base MariaDB |
| `DB_USERNAME` | `root` | Utilisateur MariaDB |
| `DB_PASSWORD` | vide | Mot de passe MariaDB |
| `JWT_SECRET` | secret de développement | Signature HS256 |
| `AI_SERVICE_ENABLED` | `true` | Active l'indexation vectorielle |
| `AI_SERVICE_BASE_URL` | `http://localhost:8001` | URL interne FastAPI |
| `AI_SERVICE_API_KEY` | `smartrecruit-local-development-key` | Clé interne partagée |
| `AI_SERVICE_TIMEOUT_SECONDS` | `30` | Timeout des appels FastAPI d'indexation/recherche |
| `AI_SERVICE_CHAT_TIMEOUT_SECONDS` | `120` | Timeout de l'appel de génération RAG |
| `AI_SERVICE_REINDEX_ALL_MAX` | `100` | Nombre maximal de documents réindexés en une fois |

> La clé de développement doit être remplacée avant tout déploiement. Elle ne doit jamais apparaître dans le frontend, les logs ou une réponse HTTP.

### 3.2 Microservice FastAPI

Créer l'environnement puis installer les dépendances :

```powershell
cd ai-service
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
```

Variables `ai-service/.env` :

| Variable | Valeur locale par défaut | Rôle |
|---|---|---|
| `AI_SERVICE_HOST` | `127.0.0.1` | Interface d'écoute |
| `AI_SERVICE_PORT` | `8001` | Port |
| `AI_SERVICE_API_KEY` | clé de développement | Doit être identique à celle de Spring |
| `CHROMA_PERSIST_DIR` | `./data/chroma` | Stockage persistant Chroma |
| `CHROMA_COLLECTION_NAME` | `hr_knowledge` | Collection Chroma |
| `EMBEDDING_MODEL_NAME` | modèle multilingue MiniLM | Modèle local |
| `EMBEDDING_DEVICE` | `cpu` | Device sentence-transformers |
| `CHUNK_SIZE` | `700` | Taille cible d'un chunk, en caractères |
| `CHUNK_OVERLAP` | `100` | Chevauchement entre chunks |
| `DEFAULT_TOP_K` | `5` | Nombre de résultats par défaut |
| `MAX_TOP_K` | `10` | Nombre maximal de résultats |
| `MIN_RELEVANCE_SCORE` | `0.30` | Seuil minimal de similarité pour le diagnostic |
| `GROQ_API_KEY` | *(clé Groq)* | Clé API pour évaluation et génération rapide (Module 4) |
| `TECH_ASSESSMENT_MODEL` | `llama-3.3-70b-versatile` | Modèle LLM d'évaluation technique |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Serveur Ollama local |
| `OLLAMA_MODEL` | `qwen3:4b` | Modèle local |
| `OLLAMA_TIMEOUT_SECONDS` | `120` | Timeout de génération Ollama |
| `OLLAMA_KEEP_ALIVE` | `10m` | Durée de maintien du modèle en mémoire |
| `OLLAMA_TEMPERATURE` | `0.1` | Température de génération |
| `OLLAMA_TOP_P` | `0.9` | Top-p |
| `OLLAMA_NUM_CTX` | `8192` | Fenêtre de contexte |
| `RAG_CHAT_TOP_K` | `3` | Nombre de chunks transmis au LLM |
| `RAG_MIN_RELEVANCE_SCORE` | `0.35` | Seuil minimal pour appeler le LLM |
| `RAG_MAX_CONTEXT_CHARS` | `12000` | Taille maximale du contexte |
| `LLM_MAX_ANSWER_CHARS` | `4000` | Taille maximale de la réponse |
| `PROMPT_VERSION` | `hr-rag-v1` | Version du prompt |
| `LLM_MAX_RETRIES` | `1` | Une seule seconde tentative contrôlée |
| `LOG_LEVEL` | `INFO` | Niveau de logs |

Le service refuse de démarrer si `CHUNK_OVERLAP >= CHUNK_SIZE`, si `DEFAULT_TOP_K > MAX_TOP_K`, ou si une valeur sort de ses bornes. Le seuil du chat (`RAG_MIN_RELEVANCE_SCORE`) est distinct du seuil de diagnostic (`MIN_RELEVANCE_SCORE`).

---

## 4. Démarrage local

Ordre recommandé :

1. **MariaDB** (XAMPP) : démarrer MySQL et créer la base `smartrecruit_ai` si nécessaire.
2. **Ollama** : démarrer le serveur local et vérifier la présence du modèle.
   ```powershell
   ollama serve
   ollama pull qwen3:4b
   ```
   L'API HTTP doit répondre sur `http://localhost:11434`.
3. **FastAPI** :
   ```powershell
   cd ai-service
   .\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001
   ```
4. **Spring Boot** :
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
5. **Angular** :
   ```powershell
   cd frontend
   npm start
   ```

Accès :

| Service | URL |
|---|---|
| Frontend | `http://localhost:4200` |
| API Spring | `http://localhost:8080` |
| Swagger Spring | `http://localhost:8080/swagger-ui.html` |
| FastAPI Swagger | `http://localhost:8001/docs` |
| Santé FastAPI | `http://localhost:8001/health` |
| Ollama | `http://localhost:11434` |

Compte administrateur initial (si aucun ADMIN n'existe) : `admin@smartrecruit.local` / `Admin123!`.

Le service FastAPI ne charge pas le modèle d'embedding au démarrage : le premier `/health` ou la première indexation déclenche le chargement paresseux. Le modèle Ollama est chargé à la première génération, puis maintenu selon `OLLAMA_KEEP_ALIVE`.

---

## 5. API

### 5.1 API publique et authentifiée Spring

| Méthode | Chemin | Accès |
|---|---|---|
| `POST` | `/api/auth/signup` | public |
| `POST` | `/api/auth/login` | public |
| `POST` | `/api/auth/logout` | authentifié |
| `GET` | `/api/users/me` | authentifié |
| `PUT` | `/api/users/me` | authentifié |
| `PUT` | `/api/users/me/password` | authentifié |
| `GET` | `/api/admin/users` | `ADMIN` |
| `GET` | `/api/admin/users/{id}` | `ADMIN` |
| `PUT` | `/api/admin/users/{id}/status` | `ADMIN` |
| `PUT` | `/api/admin/users/{id}/role` | `ADMIN` |
| `DELETE` | `/api/admin/users/{id}` | `ADMIN` |
| `GET` | `/api/hr/knowledge-documents` | authentifié |
| `GET` | `/api/hr/conversations` | authentifié, propriétaire |
| `POST` | `/api/hr/conversations` | authentifié |
| `GET` | `/api/hr/conversations/{id}` | authentifié, propriétaire |
| `PUT` | `/api/hr/conversations/{id}` | authentifié, propriétaire |
| `DELETE` | `/api/hr/conversations/{id}` | authentifié, propriétaire |
| `GET` | `/api/hr/conversations/{id}/messages` | authentifié, propriétaire, sources incluses |
| `POST` | `/api/hr/conversations/{id}/messages` | authentifié, propriétaire, rôle `USER` uniquement |
| `POST` | `/api/hr/conversations/{id}/ask` | `CANDIDATE` ou `RECRUITER`, propriétaire |

`/ask` enregistre le message USER, appelle FastAPI/Ollama hors transaction, valide la réponse et les sources, puis enregistre le message ASSISTANT, ses sources et le `AiRun`. Le client ne peut fournir que `content` : le rôle, l'utilisateur, les sources, le modèle, la version du prompt et `grounded` sont déterminés côté serveur. L'endpoint `/messages` reste disponible pour la compatibilité et ne génère aucune réponse.

### 5.2 Administration de la base de connaissances

Toutes les routes sont réservées à `ADMIN`.

| Méthode | Chemin | Description |
|---|---|---|
| `GET` | `/api/admin/hr/knowledge-documents` | Liste paginée et filtrée |
| `POST` | `/api/admin/hr/knowledge-documents` | Création + indexation |
| `GET` | `/api/admin/hr/knowledge-documents/{id}` | Détail |
| `PUT` | `/api/admin/hr/knowledge-documents/{id}` | Mise à jour + réindexation |
| `PUT` | `/api/admin/hr/knowledge-documents/{id}/status` | Activation/désactivation + index/suppression |
| `POST` | `/api/admin/hr/knowledge-documents/{id}/reindex` | Réindexation d'un document |
| `POST` | `/api/admin/hr/knowledge-documents/reindex-all` | Réindexation des documents actifs |
| `GET` | `/api/admin/hr/knowledge-documents/indexing/health` | Santé du service interne |
| `DELETE` | `/api/admin/hr/knowledge-documents/{id}` | Suppression MariaDB + vecteurs |
| `POST` | `/api/admin/hr/retrieval/search` | Diagnostic de recherche sémantique |

Filtres de liste : `page`, `size`, `sort`, `category`, `active`, `search`.
Catégories autorisées, strictement identiques des deux côtés : `FAQ`, `CANDIDATE_GUIDE`, `RECRUITER_GUIDE`, `INTERVIEW_GUIDE`, `RECRUITMENT_PROCEDURE`, `PLATFORM_HELP`, `HR_POLICY`, `OTHER`.

### 5.3 API interne FastAPI

| Méthode | Chemin | Clé requise | Description |
|---|---|---|---|
| `GET` | `/health` | non | Santé, version, Chroma, Ollama et présence du modèle |
| `POST` | `/api/v1/knowledge/index` | oui | Indexation idempotente |
| `DELETE` | `/api/v1/knowledge/{documentId}` | oui | Suppression idempotente |
| `POST` | `/api/v1/retrieval/search` | oui | Recherche de chunks |
| `POST` | `/api/v1/chat/generate` | oui | Retrieval + génération RAG locale validée |

Sécurité de la clé : `401` si le header manque, `403` si la clé est invalide. La comparaison est faite en temps constant. `/health` reste public, n'expose aucun secret et ne génère pas de réponse LLM complète. CORS n'autorise que l'origine locale configurée.

Requête d'indexation :

```json
{
  "documentId": 42,
  "title": "Guide candidat",
  "content": "Texte du document...",
  "category": "CANDIDATE_GUIDE",
  "version": 3,
  "active": true
}
```

Requête de recherche :

```json
{
  "query": "Comment postuler ?",
  "topK": 5,
  "categories": ["CANDIDATE_GUIDE"],
  "documentIds": [42]
}
```

`topK` est borné à `1..10`. `score` est une **similarité cosinus** dans `[0, 1]`, calculée comme `1 - distance_cosinus`. Ce n'est pas une probabilité ni une confiance calibrée.

Requête de génération RAG :

```json
{
  "question": "Comment postuler à une offre ?",
  "userRole": "CANDIDATE",
  "conversationId": 42,
  "topK": 3,
  "language": "fr"
}
```

Réponse (champs principaux) :

```json
{
  "answer": "Pour postuler, consultez les offres...",
  "category": "PLATFORM_HELP",
  "grounded": true,
  "needsHumanReview": false,
  "sources": [
    {
      "documentId": 10,
      "chunkId": "document-10-v0-chunk-1",
      "chunkIndex": 1,
      "title": "Comment postuler à une offre",
      "category": "PLATFORM_HELP",
      "score": 0.622264,
      "version": 0,
      "excerpt": "Les offres peuvent être recherchées..."
    }
  ],
  "sanitizedQuestion": "Comment postuler à une offre ?",
  "modelName": "qwen3:4b",
  "promptVersion": "hr-rag-v1",
  "retrievalDurationMs": 12,
  "generationDurationMs": 33100,
  "totalDurationMs": 33112,
  "promptTokenCount": 800,
  "completionTokenCount": 120
}
```

Le champ `sources` est reconstruit depuis les chunks du retrieval. Le LLM ne fournit que des `usedChunkIds` ; tout ID hors contexte est refusé. Le score affiché est une **similarité**, jamais une confiance.

### 5.4 API Module 4 : Tests techniques et Feuille de route IA

| Méthode | Chemin | Description |
|---|---|---|
| `POST` | `/api/v1/tests/generate` | Génère un test technique adaptatif personnalisé (3 QCM + 1 exercice de code) |
| `POST` | `/api/v1/tests/{id}/submit` | Évalue la soumission du candidat (note sur 100, feedback, forces/faiblesses) |
| `POST` | `/api/v1/roadmap` | Génère un plan de montée en compétences personnalisé sur 8 semaines |
| `GET` | `/api/v1/tests/{id}` | Récupère le détail d'un test technique |

Exemple de requête de génération de test :

```json
{
  "titre_poste": "Développeur Fullstack Java / Angular",
  "niveau": "Intermédiaire",
  "duree_minutes": 30
}
```

Exemple de soumission :

```json
{
  "candidat_id": "candidat-123",
  "reponses_questions": [
    {"numero": 1, "reponse": "Option B"},
    {"numero": 2, "reponse": "Option A"},
    {"numero": 3, "reponse": "Option C"}
  ],
  "reponse_exercice": "public int findMax(int[] nums) { ... }"
}
```

---

## 6. Pipeline d'indexation

1. **Validation** du document par Pydantic : identifiant positif, titre ≤ 200, contenu ≤ 100 000, catégorie stricte, version ≥ 0, `active`.
2. **Nettoyage** : normalisation des retours ligne, suppression des caractères de contrôle, compression des espaces horizontaux, conservation des accents et des écritures Unicode.
3. **Anonymisation heuristique** : remplacement des emails, téléphones, URL, IPv4, NIR français et identifiants explicitement étiquetés par des marqueurs tels que `[EMAIL]`, `[PHONE]`, `[URL]`, `[IP]`, `[IDENTIFIER]`. Aucune valeur d'origine n'est journalisée.
4. **Découpage** : paragraphes regroupés jusqu'à `CHUNK_SIZE`, phrases ou mots pour les blocs trop longs, chevauchement déterministe de `CHUNK_OVERLAP` entre chunks consécutifs.
5. **Embeddings** : encodage local des chunks avec le modèle multilingue, sans appel réseau à un LLM.
6. **Écriture Chroma** : calcul des nouveaux embeddings **avant** suppression des anciens chunks ; la collection conserve l'identifiant du document, sa version, sa catégorie et l'index du chunk.

Identifiant de chunk : `document-{documentId}-v{version}-chunk-{index}`.

### 6.1 Idempotence et remplacement

- Réindexer le même document avec la même version remplace ses chunks sans doublon.
- Toute nouvelle version remplace intégralement l'index précédent du document.
- Un document désactivé est retiré de Chroma.
- Une suppression est idempotente : supprimer un document déjà absent renvoie `deletedChunks: 0`.
- En cas d'échec du calcul d'embedding, l'ancien index n'est pas détruit.

### 6.2 États d'indexation

| État | Signification |
|---|---|
| `PENDING` | Indexation en cours (état transitoire côté orchestration) |
| `INDEXED` | Document actif indexé, `indexedAt` renseigné |
| `FAILED` | Échec du service interne, `indexingError` renseigné, document conservé |
| `NOT_INDEXED` | Document inactif ou jamais indexé |

Champs Spring : `indexingStatus`, `indexedAt`, `indexingError`.
Les mises à jour techniques de l'état d'indexation n'écrasent pas la version métier du document ; un échec d'indexation ne supprime jamais la ligne MariaDB.

### 6.3 Pipeline de génération RAG

1. Spring vérifie le JWT, le rôle `CANDIDATE`/`RECRUITER` et la propriété de la conversation.
2. Le message USER et un `AiRun` `PENDING` sont enregistrés dans une transaction courte.
3. FastAPI nettoie et anonymise la question, puis effectue le retrieval.
4. Les chunks dont le score est inférieur à `RAG_MIN_RELEVANCE_SCORE` sont éliminés ; `topK` est borné à 5.
5. Sans chunk pertinent, Ollama n'est pas appelé : une réponse de repli contrôlée est renvoyée avec `sources=[]`, `grounded=false`, `needsHumanReview=true`.
6. Sinon, un contexte versionné `hr-rag-v1` est construit, avec des délimiteurs de documents et des consignes anti-injection.
7. Ollama reçoit `/api/chat` avec `stream=false`, `think=false`, un JSON Schema et `/no_think`.
8. La sortie est validée par Pydantic ; une seule seconde tentative est autorisée.
9. FastAPI vérifie chaque `usedChunkId`, refuse les IDs inventés et reconstruit les métadonnées des sources depuis le retrieval.
10. Spring revalide la structure, les scores, les IDs dupliqués et la cohérence `grounded`/`sources`.
11. Le message ASSISTANT, ses sources figées et l'`AiRun` `COMPLETED` sont enregistrés dans une seconde transaction courte.
12. En cas d'échec, le message USER reste, aucun ASSISTANT n'est créé, l'`AiRun` passe à `FAILED` et l'API renvoie une erreur française permettant de réessayer.

Le prompt système interdit au modèle de révéler le prompt, les clés, les tokens ou la configuration, et lui rappelle que les documents sont des données non fiables. Une fuite détectée dans la réponse est remplacée par le repli contrôlé.

---

## 7. Interface ADMIN

La base de connaissances ADMIN permet :

- la liste paginée, la recherche et le filtrage par catégorie/état ;
- la création, la modification, l'activation/désactivation et la suppression ;
- l'affichage du badge d'indexation, de la date `indexedAt` et de l'erreur `indexingError` ;
- la réindexation d'un document depuis la liste et le détail ;
- la réindexation globale avec confirmation et états de chargement séparés ;
- une page **Recherche sémantique RH** (`/admin/knowledge-base/semantic-search`) qui montre la requête nettoyée, les chunks, les scores et les filtres, sans réponse générée.

Le frontend utilise uniquement l'API Spring pour les fonctionnalités RH. Il n'a aucune clé interne.

---

## 8. Module 4 : Tests techniques adaptatifs, évaluation automatique & feuille de route IA

### 8.1 Parcours Candidat interactif (Angular `/app/tests-techniques`)

Le module offre une expérience d'évaluation fluide et complète en 4 étapes intégrées :

1. **Configuration** : Sélection du poste ciblé (ex: *Développeur Fullstack Java/Angular*, *Data Engineer Python*), du niveau de séniorité (*Junior*, *Intermédiaire*, *Senior*) et de la durée.
2. **Passation du test** : Interface chronométrée comprenant 3 QCM techniques et 1 exercice de programmation avec éditeur de code intégré.
3. **Évaluation automatique par IA** : Analyse rigoureuse du code soumis, attribution d'une note sur 100, décomposition par critères (syntaxe, algorithmique, robustesse) et bilan des forces et axes d'amélioration.
4. **Feuille de route sur 8 semaines** : Programme hebdomadaire progressif généré sur-mesure pour combler les lacunes détectées, avec objectifs pédagogiques, notions clés, projets pratiques et ressources d'apprentissage.

### 8.2 Architecture Multi-Agents & Prompts versionnés

- **`TestGeneratorAgent`** : Calibre et génère les questions et l'exercice d'implémentation selon le poste et le niveau visé.
- **`EvaluationAgent`** : Analyse la solution soumise selon une grille d'évaluation multicritères stricte sans hallucination.
- **`RoadmapAgent`** : Conçoit la trajectoire d'apprentissage personnalisée étape par étape sur 8 semaines.
- **Traçabilité des prompts** : Les prompts d'évaluation et de génération font l'objet d'un versionnage documenté (`v1`, `v2`, `v3`) consigné dans `ai-service/docs/prompts-aziz.md`.

### 8.3 Persistance des données (SQLite)

Les données du Module 4 sont persistées de manière autonome dans la base locale `techmatch.db` :
- `tech_tests` : Métadonnées du test généré et énoncés.
- `tech_submissions` : Réponses fournies et code saisi par le candidat.
- `tech_evaluations` : Note finale, détail par critère et feedback IA.
- `tech_roadmaps` : Programme de formation structuré sur 8 semaines.

---

## 9. Tests

### 8.1 Backend Spring Boot

```powershell
.\mvnw.cmd test
```

Résultat de référence : **68 tests, 0 échec**. Les tests couvrent notamment l'authentification, les rôles, les utilisateurs, les conversations, la base de connaissances, la sécurité ADMIN, le client FastAPI, l'orchestration d'indexation, `/ask`, la persistance des sources, les `AiRun` et les erreurs FastAPI/Ollama.

### 8.2 Frontend Angular

```powershell
cd frontend
npm test -- --watch=false
npm run build
```

Résultat de référence : **132 tests, 22 fichiers, 0 échec** ; build de production réussi.

### 8.3 Microservice FastAPI

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m pytest
```

Résultat de référence : **135 tests réussis** (96 tests Module 1 + 39 tests Module 4), 2 ignorés (tests réels optionnels) :
- **96 tests RAG RH (Module 1)** : Anonymisation, chunking, ChromaDB, prompts et Ollama.
- **39 tests Évaluation Technique (Module 4)** : Validation des schémas Pydantic, prompts adaptatifs, scoring d'évaluation, génération de roadmap et persistance SQLite (`tests/test_tech_assessment/`).

Les tests réels sont optionnels :

```powershell
$env:RUN_REAL_EMBEDDING_TEST='1'
.\.venv\Scripts\python.exe -m pytest tests/test_real_embedding.py -q -m real_embedding

$env:RUN_REAL_OLLAMA_TEST='1'
.\.venv\Scripts\python.exe -m pytest tests/test_real_ollama.py -q -m real_ollama
```

Les tests unitaires utilisent des embeddings factices, un client Ollama simulé et un stockage Chroma temporaire pour ne pas dépendre du réseau ni du téléchargement du modèle. Le test réel Ollama a été exécuté avec `qwen3:4b` et a validé une sortie JSON structurée.

---

## 10. Validation de bout en bout réalisée

### 9.1 Socle documentaire sans LLM

La validation manuelle du socle existant a couvert :

- `GET /health` FastAPI et `GET .../indexing/health` Spring ;
- absence de clé → `401`, clé invalide → `403` sur FastAPI ;
- création d'un document ADMIN → `INDEXED` avec date ;
- recherche sémantique → chunk attendu, score de similarité et filtres ;
- anonymisation des emails et téléphones dans le chunk retourné ;
- mise à jour du document → nouvelle version et ancien chunk remplacé ;
- désactivation → `NOT_INDEXED` et absence de résultat ;
- réactivation → `INDEXED` ;
- réindexation unitaire et réindexation globale ;
- arrêt de FastAPI → `FAILED` côté Spring, document MariaDB conservé, recherche Spring en `503` ;
- redémarrage de FastAPI → réindexation réussie ;
- suppression → document MariaDB supprimé, vecteurs supprimés, recherche vide ;
- vérification SQL : aucun message `ASSISTANT` créé, aucune conversation ajoutée par la recherche ;
- parcours Angular : connexion ADMIN, liste avec badges, réindexation depuis la liste, page de recherche sémantique avec résultats.

### 9.2 Chaîne RAG locale Spring → FastAPI → Ollama

Huit scénarios ont été exécutés sur les services réels (`qwen3:4b`) :

| # | Scénario | Résultat observé |
|---|---|---|
| 1 | Question CANDIDATE dans le domaine | `200`, réponse `grounded=true`, source validée, message ASSISTANT et `AiRun=COMPLETED` persistés |
| 2 | Question hors domaine | `200`, repli contrôlé, `grounded=false`, `needsHumanReview=true`, `sources=[]`, aucun appel Ollama |
| 3 | Conversation appartenant à un autre candidat | `404`, aucune donnée révélée |
| 4 | Rôle ADMIN sur `/ask` | `403` |
| 5 | Rôle RECRUITER sur `/ask` | `200`, message ASSISTANT persisté |
| 6 | Email et téléphone dans la question | question anonymisée en `[EMAIL]` / `[PHONE]` avant retrieval, prompt et Ollama |
| 7 | Tentative d'injection de prompt | repli contrôlé, aucune fuite du prompt système, de la clé ou du JWT |
| 8 | Ollama arrêté puis redémarré | `503` français, message USER conservé, aucun faux ASSISTANT, `AiRun=FAILED` / `OLLAMA_UNAVAILABLE` ; après redémarrage, la même question est rejouée avec succès (`200`, source validée) |

La vérification SQL du scénario 8 a confirmé : `USER=2`, `ASSISTANT=1` après l'échec, puis `USER=4`, `ASSISTANT=2` après la reprise. Aucun message ASSISTANT n'est créé en cas d'échec.

### 9.3 Évaluation de qualité RAG

Une campagne locale de **20 questions** (guides candidats, suivi de candidature, confidentialité, aide plateforme, questions hors domaine, injection et données personnelles) a été exécutée sur FastAPI avec `qwen3:4b` :

- **20/20** requêtes HTTP valides ;
- **14/20** réponses fondées avec au moins une source validée ;
- **6/20** replis contrôlés sans source ;
- **0** source inventée persistée (les IDs inconnus sont rejetés) ;
- temps moyen observé : environ **16 secondes** par génération sur CPU, avec un premier appel plus long.

L'analyse manuelle a relevé deux limites sémantiques : une question sur la « recherche d'offres d'emploi » a reçu une réponse sur le « processus de recrutement », et une question sur la « création d'une alerte » a reçu une réponse sur les notifications de suivi. Le corpus ne contient pas de document dédié à ces deux sujets. La validation structurelle, l'affichage des sources et le repli fonctionnent, mais la pertinence sémantique reste perfectible. Ces points sont documentés dans les limites connues.

Les données temporaires créées pour ces essais (utilisateurs, conversations, messages, runs, sources) ont été supprimées. Les documents RH légitimes et les comptes existants ont été préservés.

---

## 11. Sécurité

- JWT pour l'API Spring ; autorisations par rôle et propriété des conversations.
- Clé interne FastAPI uniquement côté Spring, jamais dans Angular.
- Endpoints internes protégés par `X-AI-Service-Key` ; `/health` public.
- Aucun CORS navigateur n'est ouvert côté FastAPI : les appels sont strictement serveur à serveur, via Spring Boot.
- Aucune clé, aucun contenu de document, aucun prompt et aucune donnée personnelle d'origine dans les logs.
- Anonymisation de la question avant retrieval, prompt et appel Ollama ; les emails, téléphones, URL, IP, identifiants, mots de passe, tokens et clés étiquetés sont remplacés.
- Le prompt système interdit la révélation du prompt, des clés, des tokens et de la configuration ; les documents sont traités comme des données non fiables.
- Le LLM ne décide pas de l'acceptation ou du refus d'un candidat et ne classe pas les candidats.
- Les sources sont validées et reconstruites côté serveur ; aucun ID inventé n'est persisté.
- Les réponses invalides ne sont jamais enregistrées ; en cas d'échec, le message USER reste et l'`AiRun` passe à `FAILED`.
- Service FastAPI destiné à une écoute locale (`127.0.0.1`) en développement.
- Aucun fournisseur cloud, aucune clé API externe, aucun envoi de données à un LLM distant.

---

## 12. Limites connues

- Le modèle local `qwen3:4b` peut produire des réponses incomplètes ou maladroites ; la validation structurelle ne garantit pas la perfection sémantique.
- L'anonymisation est **heuristique** : les noms non étiquetés, adresses postales et identifiants de formats inhabituels peuvent ne pas être détectés.
- Les scores sont des similarités cosinus, non des probabilités ni des confiances calibrées.
- Le seuil de similarité peut produire des faux positifs ou des replis excessifs selon la formulation de la question. L'évaluation a notamment montré des réponses hors sujet lorsque le corpus ne couvre pas exactement la question.
- ChromaDB est un index dérivé : une perte de l'index se répare par réindexation depuis MariaDB.
- La réindexation globale est volontairement bornée par `AI_SERVICE_REINDEX_ALL_MAX`.
- Pas de file d'attente asynchrone : l'indexation et la génération sont synchrones et peuvent prendre du temps (plusieurs dizaines de secondes avec `qwen3:4b` sur CPU).
- Pas de fine-tuning ni d'évaluation à grande échelle : la campagne locale de 20 questions donne un ordre de grandeur, pas une garantie de qualité. Le prompt `hr-rag-v1` doit être versionné et testé avant toute évolution.

---

## 13. Dépannage

| Symptôme | Cause probable | Action |
|---|---|---|
| `401` sur FastAPI | header `X-AI-Service-Key` absent | vérifier `AI_SERVICE_API_KEY` |
| `403` sur FastAPI | clé différente entre Spring et FastAPI | aligner les deux `.env` |
| `503` côté Spring | FastAPI arrêté ou timeout | démarrer FastAPI, vérifier `/health` |
| `503` avec « modèle local indisponible » | Ollama arrêté ou modèle absent | démarrer Ollama, exécuter `ollama pull qwen3:4b` |
| `504` sur `/ask` | génération plus longue que le timeout | augmenter `OLLAMA_TIMEOUT_SECONDS` / `AI_SERVICE_CHAT_TIMEOUT_SECONDS` |
| `502` sur `/ask` | JSON Ollama invalide ou source inventée | vérifier les logs FastAPI, relancer la question |
| `FAILED` avec « service d'indexation indisponible » | FastAPI inaccessible | redémarrer puis réindexer |
| Premier appel très long | téléchargement du modèle d'embedding ou chargement de `qwen3:4b` | attendre, vérifier le cache Hugging Face et Ollama |
| Résultats vides | seuil de similarité ou document inactif | vérifier `MIN_RELEVANCE_SCORE`, `RAG_MIN_RELEVANCE_SCORE` et l'état d'indexation |
| `500` Chroma au démarrage | répertoire persistant verrouillé | arrêter les autres processus utilisant `data/chroma` |
| CORS dans Angular | origine différente de `http://localhost:4200` | utiliser exactement l'origine autorisée |

---

## 14. Arborescence utile

```text
.
├── src/main/java/com/smartrecruit/       # backend Spring Boot
│   ├── controller/                        # API et endpoints ADMIN
│   ├── service/                           # métier et orchestration d'indexation
│   ├── integration/ai/                    # client FastAPI et gestion des erreurs
│   └── entity/, repository/, dto/         # persistance et contrats
├── src/test/                              # 68 tests Spring Boot
├── frontend/                              # Angular 22
│   ├── src/app/features/hr-assistant/     # chat, bulles et sources (Module 1)
│   ├── src/app/features/tech-assessment/  # parcours complet tests techniques (Module 4)
│   └── src/app/core/services/             # services API (Auth, HR, TechAssessment)
├── ai-service/                            # FastAPI + embeddings + ChromaDB + Ollama + Groq
│   ├── app/api/                           # endpoints internes (chat, knowledge, tech_assessment)
│   ├── app/coreAgents/Agents/tech_assessment/ # agents Generator, Evaluator, Roadmap (Module 4)
│   ├── app/coreAgents/llm_client.py       # client LLM hybride Groq / Ollama
│   ├── app/db/models/tech_assessment.py   # modèles SQLite (tests, submissions, roadmaps)
│   ├── app/services/                      # nettoyage, anonymisation, chunks, embeddings, index, RAG (Module 1)
│   ├── docs/prompts-aziz.md               # versionnage et benchmarks des prompts (v1/v2/v3)
│   └── tests/                             # 135 tests automatisés (96 Module 1 + 39 Module 4)
├── .env.example                           # configuration Spring
└── ai-service/.env.example                # configuration FastAPI
```

Les changements de cette phase restent **non commités** : aucun commit Git n'a été créé.


---

## 15. Auteurs & Contributeurs

Projet réalisé dans le cadre du module *« IA for Software Engineering »* (ESPRIT - 5SAE, 2026-2027) :
- **Sahar Khiari** : Conception et développement du **Module 1** (Assistant RH, Base documentaire vectorielle RAG locale Ollama/ChromaDB, intégration Spring Boot).
- **Mohamed Aziz Guizani** : Conception et développement du **Module 4** (Tests techniques adaptatifs, Évaluation automatique par IA, Feuille de route d'apprentissage sur 8 semaines, architecture multi-agents LLM et intégration Angular).
