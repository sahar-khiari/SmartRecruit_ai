# SmartRecruit AI — Microservice RAG local

Service interne **FastAPI** chargé de transformer les documents RH de SmartRecruit AI en index vectoriel local, de retrouver les passages les plus pertinents, puis de générer une réponse RAG avec **Ollama** et le modèle local **qwen3:4b**.

> **100 % local. Aucun fournisseur cloud, aucune clé API externe.**
>
> Le service ne produit un message `ASSISTANT` que via Spring Boot, après validation stricte. Il n'est jamais appelé directement par Angular et ne reçoit jamais de JWT utilisateur.

---

## 1. Rôle et architecture

```text
Spring Boot (source de vérité MariaDB)
    │  POST /api/v1/knowledge/index
    │  DELETE /api/v1/knowledge/{documentId}
    │  POST /api/v1/retrieval/search
    │  POST /api/v1/chat/generate
    │  Header: X-AI-Service-Key
    ▼
FastAPI (127.0.0.1:8001)
    ├── nettoyage du texte
    ├── anonymisation heuristique
    ├── découpage déterministe avec chevauchement
    ├── embeddings locaux sentence-transformers
    ├── ChromaDB persistant
    ├── retrieval filtré (topK, seuil)
    ├── prompt versionné hr-rag-v1
    └── client Ollama
           │
           ▼
       Ollama (localhost:11434) → qwen3:4b
```

Principes :

- MariaDB reste la source de vérité documentaire.
- ChromaDB est un index dérivé, reconstructible par réindexation.
- L'indexation et la suppression sont **idempotentes**.
- Les nouveaux embeddings sont calculés avant de supprimer les anciens chunks.
- Un échec d'embedding ne détruit pas l'index précédent.
- Le retrieval précède toujours la construction du prompt et l'appel Ollama.
- Les documents sont traités comme des **données non fiables**, jamais comme des instructions.
- Aucun document, prompt complet, email, téléphone ou secret n'est journalisé.
- Une réponse LLM n'est renvoyée qu'après validation Pydantic, vérification des `usedChunkIds` et reconstruction des sources.

---

## 2. Stack et versions

| Composant | Version |
|---|---|
| Python | **3.11.9** |
| FastAPI | **0.115.14** |
| Uvicorn | version épinglée dans `requirements.txt` |
| ChromaDB | **1.0.21** |
| sentence-transformers | **5.1.2** |
| PyTorch | **2.7.1+cpu** |
| Modèle d'embedding | `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` |
| Ollama | local, port `11434` |
| Modèle LLM | `qwen3:4b` |

Le service cible Python `>=3.11,<3.13`. Le premier chargement du modèle d'embedding télécharge environ 470 Mo dans le cache Hugging Face. Le modèle Ollama doit être récupéré une fois avec `ollama pull qwen3:4b`.

---

## 3. Installation

```powershell
cd ai-service
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
```

### 3.1 Configuration

Le fichier `.env` est optionnel : les variables d'environnement ont priorité.

```dotenv
AI_SERVICE_HOST=127.0.0.1
AI_SERVICE_PORT=8001
AI_SERVICE_API_KEY=smartrecruit-local-development-key

CHROMA_PERSIST_DIR=./data/chroma
CHROMA_COLLECTION_NAME=hr_knowledge

EMBEDDING_MODEL_NAME=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
EMBEDDING_DEVICE=cpu

CHUNK_SIZE=700
CHUNK_OVERLAP=100
DEFAULT_TOP_K=5
MAX_TOP_K=10
MIN_RELEVANCE_SCORE=0.30

OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:4b
OLLAMA_TIMEOUT_SECONDS=120
OLLAMA_KEEP_ALIVE=10m
OLLAMA_TEMPERATURE=0.1
OLLAMA_TOP_P=0.9
OLLAMA_NUM_CTX=8192

RAG_CHAT_TOP_K=3
RAG_MIN_RELEVANCE_SCORE=0.35
RAG_MAX_CONTEXT_CHARS=12000
LLM_MAX_ANSWER_CHARS=4000
PROMPT_VERSION=hr-rag-v1
LLM_MAX_RETRIES=1

LOG_LEVEL=INFO
```

Contraintes validées au démarrage :

- `CHUNK_SIZE` entre `200` et `2000` ;
- `CHUNK_OVERLAP` entre `0` et `1000`, strictement inférieur à `CHUNK_SIZE` ;
- `DEFAULT_TOP_K` entre `1` et `10`, inférieur ou égal à `MAX_TOP_K` ;
- `MAX_TOP_K` entre `1` et `20` ;
- `MIN_RELEVANCE_SCORE` entre `0.0` et `1.0` ;
- `RAG_CHAT_TOP_K` entre `1` et `5` ;
- `RAG_MIN_RELEVANCE_SCORE` entre `0.0` et `1.0` ;
- `OLLAMA_TIMEOUT_SECONDS` strictement positif ;
- `LLM_MAX_RETRIES` entre `0` et `1`.

> La valeur `AI_SERVICE_API_KEY` doit être **strictement identique** à celle du backend Spring. Elle ne doit jamais être transmise au frontend.

### 3.2 Démarrage

Prérequis Ollama :

```powershell
ollama serve
ollama pull qwen3:4b
```

Puis :

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

- Swagger : `http://127.0.0.1:8001/docs`
- Santé : `http://127.0.0.1:8001/health`
- Ollama : `http://localhost:11434`

Le modèle d'embedding n'est pas chargé au démarrage. Le premier `/health`, la première indexation ou la première recherche déclenche son chargement paresseux. Le modèle Ollama est chargé à la première génération, puis maintenu selon `OLLAMA_KEEP_ALIVE`.

---

## 4. Endpoints

### 4.1 `GET /health` — public

```json
{
  "status": "UP",
  "service": "smartrecruit-ai-service",
  "version": "1.1.0",
  "embeddingModel": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "chromaAvailable": true,
  "ollamaAvailable": true,
  "llmModel": "qwen3:4b",
  "llmModelAvailable": true,
  "timestamp": "2026-10-24T14:12:38.381453Z"
}
```

`status` vaut `DEGRADED` si le modèle d'embedding ou Chroma n'est pas disponible. `ollamaAvailable` et `llmModelAvailable` sont des diagnostics : `/health` ne génère **aucune** réponse LLM complète. Aucun secret ni chemin sensible n'est exposé.

### 4.2 `POST /api/v1/knowledge/index` — clé requise

```json
{
  "documentId": 42,
  "title": "Guide candidat",
  "content": "Contenu du document...",
  "category": "CANDIDATE_GUIDE",
  "version": 3,
  "active": true
}
```

Contraintes : `documentId > 0`, titre `1..200`, contenu `1..100000`, catégorie stricte, `version >= 0`, `active` booléen. Les champs inconnus sont refusés.

Réponse :

```json
{
  "documentId": 42,
  "version": 3,
  "status": "INDEXED",
  "chunksIndexed": 2,
  "anonymizedItemsCount": 1,
  "modelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "indexedAt": "2026-10-24T14:11:58.122898Z"
}
```

Un document inactif est accepté mais n'est pas indexé : ses anciens chunks sont supprimés et `chunksIndexed` vaut `0`.

### 4.3 `DELETE /api/v1/knowledge/{documentId}` — clé requise

```json
{
  "documentId": 42,
  "deletedChunks": 2,
  "status": "DELETED"
}
```

Supprimer un document absent est une réussite idempotente avec `deletedChunks: 0`.

### 4.4 `POST /api/v1/retrieval/search` — clé requise

```json
{
  "query": "Comment postuler ?",
  "topK": 5,
  "categories": ["CANDIDATE_GUIDE"],
  "documentIds": [42]
}
```

- `query` : `1..2000` caractères.
- `topK` : `1..10`, valeur par défaut `5`.
- `categories` : filtre optionnel, valeurs strictement identiques à l'enum Spring.
- `documentIds` : filtre optionnel, dédupliqué et trié.

Réponse :

```json
{
  "query": "Comment postuler ?",
  "sanitizedQuery": "Comment postuler ?",
  "results": [
    {
      "documentId": 42,
      "chunkId": "document-42-v3-chunk-0",
      "chunkIndex": 0,
      "title": "Guide candidat",
      "category": "CANDIDATE_GUIDE",
      "content": "Pour postuler, ouvrez l'offre puis cliquez sur Postuler...",
      "score": 0.78,
      "version": 3
    }
  ],
  "totalResults": 1,
  "modelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "durationMs": 27
}
```

`score = 1 - distance_cosinus`, borné dans `[0, 1]`. C'est une **similarité**, pas une probabilité. Les résultats sous `MIN_RELEVANCE_SCORE` sont écartés.

### 4.5 `POST /api/v1/chat/generate` — clé requise

```json
{
  "question": "Comment postuler à une offre ?",
  "userRole": "CANDIDATE",
  "conversationId": 42,
  "topK": 3,
  "language": "fr"
}
```

Contraintes : `question` `1..2000`, `topK` `1..5`, `userRole` limité à `CANDIDATE`/`RECRUITER`, champs inconnus refusés. Aucun `userId`, JWT, source, modèle ou prompt n'est accepté.

Traitement :

1. nettoyage et anonymisation de la question ;
2. retrieval des chunks pertinents ;
3. filtrage sous `RAG_MIN_RELEVANCE_SCORE` ;
4. repli contrôlé sans appel Ollama si aucun chunk ne passe ;
5. sinon prompt `hr-rag-v1`, appel Ollama `/api/chat` non streamé avec JSON Schema ;
6. validation Pydantic stricte, une seule tentative contrôlée ;
7. validation des `usedChunkIds` et reconstruction des sources ;
8. réponse finale avec `grounded`, `needsHumanReview` et sources fiables.

Exemple de réponse :

```json
{
  "answer": "Pour postuler, consultez les offres depuis votre espace candidat...",
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
      "score": 0.62,
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

Erreurs contrôlées : `503` si Ollama est indisponible ou le modèle absent, `504` si le timeout est dépassé, `502` si la sortie LLM est invalide ou contient un chunk ID inventé.

### 4.6 Sécurité

```powershell
curl.exe -X POST http://127.0.0.1:8001/api/v1/retrieval/search `
  -H "Content-Type: application/json" `
  -H "X-AI-Service-Key: smartrecruit-local-development-key" `
  --data-binary "@search.json"
```

| Situation | Réponse |
|---|---|
| header absent ou vide | `401` |
| clé incorrecte | `403` |
| clé correcte | traitement normal |
| `/health` | public |

La comparaison de clé utilise `secrets.compare_digest`. Le service n'émet **aucun en-tête CORS** : un navigateur ne peut donc pas l'appeler directement. Les échanges sont strictement serveur à serveur, entre Spring Boot et FastAPI.

---

## 5. Pipeline de traitement

### 5.1 Nettoyage

- normalise `\r\n` et `\r` en `\n` ;
- supprime les caractères de contrôle ;
- compresse les espaces horizontaux (espaces, tabulations, insécables) ;
- supprime les lignes vides en excès ;
- conserve les accents, l'arabe et les autres écritures Unicode ;
- ne résume pas et ne reformule pas.

Un texte vide après nettoyage est refusé.

### 5.2 Anonymisation

Remplacements avant indexation et avant recherche :

| Donnée | Marqueur |
|---|---|
| email | `[EMAIL]` |
| téléphone français/international | `[PHONE]` |
| URL | `[URL]` |
| adresse IPv4 | `[IP]` |
| mot de passe, token, clé API ou secret étiqueté | `[SECRET]` |
| JWT brut | `[TOKEN]` |
| NIR français à 15 chiffres | `[IDENTIFIER]` |
| identifiant étiqueté (`CIN:`, `NIR :`, `passeport #`, etc.) | `[IDENTIFIER]` |
| nom explicitement étiqueté (`Nom :`, `Candidat :`, `Madame :`, etc.) | `[NAME]` |

Limites assumées :

- les noms non étiquetés, adresses postales et identifiants de formats inhabituels peuvent ne pas être détectés ;
- l'anonymisation réduit le risque mais ne garantit pas l'absence totale de donnée personnelle ;
- aucune valeur d'origine n'est journalisée ni renvoyée.

### 5.3 Découpage

- taille cible : `CHUNK_SIZE` caractères ;
- les paragraphes sont regroupés tant que la taille cible le permet ;
- les paragraphes trop longs sont découpés aux frontières de phrases, puis de mots ;
- un chevauchement de `CHUNK_OVERLAP` caractères est réutilisé entre chunks consécutifs ;
- l'algorithme est déterministe : même entrée, même découpage ;
- identifiant : `document-{documentId}-v{version}-chunk-{index}`.

### 5.4 Embeddings et ChromaDB

- encodage local des chunks avec le modèle multilingue ;
- normalisation des vecteurs pour la similarité cosinus ;
- collection persistante `hr_knowledge` ;
- métadonnées conservées : identifiant document, version, catégorie, index du chunk, titre ;
- stockage sur disque dans `data/chroma` par défaut.

### 5.5 Ordre de remplacement

1. Nettoyage, anonymisation et découpage.
2. Calcul de tous les nouveaux embeddings.
3. Suppression des anciens chunks du document.
4. Insertion des nouveaux chunks.

Si l'étape 2 échoue, les anciens chunks restent en place. ChromaDB n'est jamais la source de vérité : la réindexation depuis MariaDB reconstruit l'index.

### 5.6 Génération RAG et garde-fous

- La question est anonymisée **avant** le retrieval, le prompt et l'appel Ollama.
- Le prompt `hr-rag-v1` interdit de révéler le prompt système, les clés, les tokens, les secrets ou la configuration.
- Les documents sont encadrés par des délimiteurs et présentés comme des données non fiables ; toute instruction contenue dans un document est ignorée.
- Le modèle ne décide jamais de l'acceptation, du classement ou du rejet d'un candidat.
- Le modèle ne choisit pas les métadonnées des sources : il ne fournit que des `usedChunkIds`.
- Un ID absent des chunks récupérés est refusé ; les sources sont reconstruites depuis les données de retrieval.
- Une réponse non conforme au JSON Schema est rejetée. Une seule seconde tentative contrôlée est autorisée.
- Une fuite de prompt ou de secret détectée dans la réponse déclenche le repli contrôlé.
- Aucune réponse LLM non validée n'est renvoyée à Spring.

---

## 6. Catégories

Valeurs strictement alignées avec l'enum Java :

`FAQ`, `CANDIDATE_GUIDE`, `RECRUITER_GUIDE`, `INTERVIEW_GUIDE`, `RECRUITMENT_PROCEDURE`, `PLATFORM_HELP`, `HR_POLICY`, `OTHER`.

Toute autre valeur produit une erreur de validation `422`.

---

## 7. Tests

```powershell
.\.venv\Scripts\python.exe -m pytest
```

Résultat de référence : **96 tests réussis, 2 ignorés**. Les tests ignorés sont le test réel d'embedding et le test réel Ollama, désactivés par défaut pour ne pas imposer le téléchargement ni le serveur Ollama.

Activer les tests réels :

```powershell
$env:RUN_REAL_EMBEDDING_TEST='1'
.\.venv\Scripts\python.exe -m pytest tests/test_real_embedding.py -q -m real_embedding

$env:RUN_REAL_OLLAMA_TEST='1'
.\.venv\Scripts\python.exe -m pytest tests/test_real_ollama.py -q -m real_ollama
```

La suite couvre : configuration, nettoyage, anonymisation et faux positifs, découpage, identifiants, modèle factice, Chroma temporaire, sécurité de la clé, validation des contrats, indexation, remplacement, désactivation, suppression idempotente, filtres, topK, scores, prompt, schéma strict, retry contrôlé, repli, sources inventées, injection de prompt, indisponibilité Ollama, timeout et gestion d'erreurs.

Les tests unitaires utilisent :

- des embeddings factices déterministes ;
- un répertoire Chroma temporaire ;
- une clé de test ;
- un client Ollama simulé ;
- aucun appel réseau à un LLM.

Le test `tests/test_real_ollama.py` a été exécuté avec `qwen3:4b` et a validé une sortie JSON structurée.

---

## 8. Arborescence

```text
ai-service/
├── app/
│   ├── main.py                  # assemblage FastAPI, CORS, erreurs, docs
│   ├── core/
│   │   ├── config.py            # Settings Pydantic
│   │   ├── security.py          # X-AI-Service-Key
│   │   └── logging_config.py
│   ├── api/
│   │   ├── health.py
│   │   ├── knowledge.py
│   │   ├── retrieval.py
│   │   └── chat.py
│   ├── models/                  # contrats Pydantic stricts
│   ├── services/
│   │   ├── text_cleaning_service.py
│   │   ├── anonymization_service.py
│   │   ├── chunking_service.py
│   │   ├── embedding_service.py
│   │   ├── indexing_service.py
│   │   ├── vector_store_service.py
│   │   ├── retrieval_service.py
│   │   ├── ollama_client.py
│   │   ├── prompt_service.py
│   │   └── chat_service.py
│   └── utils/identifiers.py
├── tests/
├── requirements.txt
├── pyproject.toml
└── .env.example
```

---

## 9. Limites et dépannage

| Symptôme | Cause probable | Action |
|---|---|---|
| `401` | clé absente | ajouter `X-AI-Service-Key` |
| `403` | clé incorrecte | aligner Spring et FastAPI |
| `422` | catégorie inconnue, champ interdit, contenu trop long | vérifier le contrat |
| `DEGRADED` | modèle d'embedding ou Chroma indisponible | consulter les logs, vérifier `data/chroma` |
| Premier appel long | téléchargement du modèle d'embedding ou chargement de `qwen3:4b` | attendre la fin du cache / le chargement Ollama |
| `503` sur `/chat/generate` | Ollama arrêté ou modèle absent | démarrer Ollama, exécuter `ollama pull qwen3:4b` |
| `504` sur `/chat/generate` | génération plus longue que `OLLAMA_TIMEOUT_SECONDS` | augmenter le timeout ou réduire le contexte |
| `502` sur `/chat/generate` | JSON invalide, fuite détectée ou chunk ID inventé | consulter les logs, relancer la question |
| `database is locked` | deux processus écrivent dans le même répertoire Chroma | arrêter les autres instances |
| Résultats vides | score sous le seuil ou filtre trop restrictif | baisser `MIN_RELEVANCE_SCORE` ou élargir les filtres |

Limites de périmètre : pas d'OCR, pas de file d'attente asynchrone, anonymisation heuristique, qualité dépendante du modèle local `qwen3:4b`, pas de fine-tuning. La génération est synchrone et peut prendre plusieurs dizaines de secondes sur CPU.
