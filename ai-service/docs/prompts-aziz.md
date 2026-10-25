# Journal de prompts — Module 4 (Tests techniques, évaluation, feuille de route)
**Auteur : Aziz | Branche : module4-aziz | Cours : IA for SE, ESPRIT A.U 2026-2027**

> Ce fichier documente chaque interaction avec un LLM : le prompt exact utilisé (v1, v2, v3),
> le modèle, le résultat, les corrections apportées et la justification du choix.
> Il est requis pour la notation individuelle (2 pts sur 8 selon la grille du cours).
>
> **Emplacement du code source dans le projet :**
> - Fichier de définition des prompts : [`ai-service/app/coreAgents/Agents/tech_assessment/prompts.py`](file:///C:/Users/mouha/Desktop/SmartRecruit_ai/ai-service/app/coreAgents/Agents/tech_assessment/prompts.py)
> - Service d'exécution et A/B testing : [`ai-service/app/coreAgents/Agents/tech_assessment/generator.py`](file:///C:/Users/mouha/Desktop/SmartRecruit_ai/ai-service/app/coreAgents/Agents/tech_assessment/generator.py)

---

## 1. Vue d'ensemble des 3 versions de prompt pour la génération de test

Pour répondre aux exigences d'ingénierie de prompt (comparaison de plusieurs techniques d'itération), nous avons développé et testé trois variantes d'ingénierie de prompt :

1. **Version 1 (v1 — Zéro-shot direct / Minimal)** : consigne basique et directe, sans règles fines de calibration.
2. **Version 2 (v2 — Structuré & Contraintes négatives/positives)** : prompt segmenté en sections Markdown (`## MISSION`, `## CONTEXTE`, `## RÈGLES OBLIGATOIRES`) avec contraintes strictes sur les choix (4 choix A-D) et pondération des points.
3. **Version 3 (v3 — Chain-of-Thought / Raisonnement pas-à-pas)** : guidage cognitif forçant le LLM à analyser la criticité des compétences et la validité des critères avant d'émettre le JSON final.

---

## 2. Texte intégral des 3 versions de prompt (Code & Templates)

### 2.1 Version 1 (v1) — Zéro-shot minimal

**Implémentation :** Fonction `build_prompt_generate_v1()` dans `prompts.py`.

#### Prompt Système (System Prompt)
```text
Tu es un expert en recrutement IT. Tu génères des tests techniques objectifs et adaptés au poste. Tu réponds UNIQUEMENT avec un objet JSON valide, sans texte supplémentaire.
```

#### Prompt Utilisateur (User Prompt)
```text
Génère un test technique pour le poste suivant.

POSTE : {job_title}
DESCRIPTION : {job_description}

COMPÉTENCES À ÉVALUER (lacunes du candidat) :
{competences_str}

CONTRAINTES :
- Exactement 5 questions (mélange QCM et questions ouvertes)
- 1 exercice pratique avec des critères de correction clairs
- Chaque question vise une des compétences listées
- Les QCM ont exactement 4 choix (A, B, C, D) avec une seule bonne réponse
- Le champ reponse_correcte est obligatoire pour chaque question

Réponds avec ce schéma JSON exact :
{schema_str}
```

---

### 2.2 Version 2 (v2) — Structuré avec contraintes explicites (Production)

**Implémentation :** Fonction `build_prompt_generate_v2()` dans `prompts.py`.  
*Version retenue par défaut en production pour son équilibre parfait latence/qualité/respect du schéma.*

#### Prompt Système (System Prompt)
```text
Tu es un expert en évaluation technique pour le recrutement IT. Tu crées des tests équilibrés, pertinents et non-discriminatoires. Tes questions sont claires, précises et adaptées au niveau demandé. Tu réponds UNIQUEMENT avec un objet JSON valide, aucun texte avant ou après.
```

#### Prompt Utilisateur (User Prompt)
```text
## MISSION
Génère un test technique complet pour évaluer un candidat sur les compétences manquantes ci-dessous.

## CONTEXTE DU POSTE
- Intitulé : {job_title}
- Description : {job_description}

## COMPÉTENCES À ÉVALUER
{competences_str}

## RÈGLES OBLIGATOIRES

### Questions (5 au total) :
- 3 questions QCM et 2 questions ouvertes (ou 2 QCM + 3 ouvertes)
- Chaque question cible UNE compétence de la liste ci-dessus
- QCM : exactement 4 choix (lettres A, B, C, D), une seule bonne réponse, valeur : 2 points chacune
- Question ouverte : réponse attendue en 3-5 phrases, valeur : 4 points chacune
- Le champ "reponse_correcte" est OBLIGATOIRE pour toutes les questions

### Exercice pratique (1 au total) :
- Durée estimée : 15-20 minutes
- Cibler la compétence la plus critique
- Fournir 3 à 5 critères de correction mesurables et objectifs
- Valeur : 10 points

### Format :
- Le titre du test doit mentionner le poste
- Durée totale recommandée : 45 minutes

## FORMAT DE RÉPONSE (JSON strict)
{schema_str}
```

---

### 2.3 Version 3 (v3) — Chaîne de pensée (Chain-of-Thought - CoT)

**Implémentation :** Fonction `build_prompt_generate_v3()` dans `prompts.py`.  
*Utilisée pour les postes seniors / complexes nécessitant une analyse préalable approfondie.*

#### Prompt Système (System Prompt)
```text
Tu es un expert senior en ingénierie logicielle et en évaluation technique. Tu conçois des tests qui mesurent précisément les compétences pratiques. Avant de générer le test, tu analyses le poste et les lacunes pour choisir les meilleures questions. Tu réponds UNIQUEMENT avec un objet JSON valide.
```

#### Prompt Utilisateur (User Prompt)
```text
## CONTEXTE
Poste : {job_title}
Description : {job_description}

## COMPÉTENCES LACUNAIRES DU CANDIDAT
{competences_str}

## ÉTAPES DE RÉFLEXION (avant de générer)
1. Identifie les 2-3 compétences les plus critiques pour ce poste
2. Pour chaque compétence critique, choisis le meilleur type de question (QCM si la réponse est binaire/factuelle, ouverte si elle nécessite un raisonnement)
3. Assure-toi que chaque question est différente des autres et couvre une facette différente
4. Vérifie que l'exercice pratique est réaliste et réalisable en 15-20 minutes
5. Vérifie que les critères de correction sont objectifs (pas de "bonne rédaction" mais "mentionne X et Y")

## RÈGLES DE GÉNÉRATION
- Exactement 5 questions (numérotées 1 à 5)
- Exactement 1 exercice pratique
- QCM : 4 choix A/B/C/D, 1 seule bonne réponse, reponse_correcte obligatoire
- Question ouverte : reponse_correcte = les éléments clés attendus
- Exercice : 3 à 5 critères de correction concrets et mesurables

## SCHÉMA JSON DE SORTIE (STRICTEMENT RESPECTÉ)
{schema_str}

Génère maintenant le test en JSON pur, sans commentaire ni introduction.
```

---

## 3. Schémas JSON stricts injectés (`OUTPUT_SCHEMA_GENERATE`)

Toute requête injecte le schéma JSON ci-dessous dans la variable `{schema_str}` pour forcer une sortie 100% conforme et parsable par Pydantic :

```json
{
  "type": "object",
  "required": ["titre", "job_title", "duree_minutes", "questions", "exercice", "instructions"],
  "properties": {
    "titre": {"type": "string"},
    "job_title": {"type": "string"},
    "duree_minutes": {"type": "integer", "minimum": 10, "maximum": 120},
    "questions": {
      "type": "array",
      "minItems": 5,
      "maxItems": 5,
      "items": {
        "type": "object",
        "required": ["numero", "type", "enonce", "competence_ciblee", "points"],
        "properties": {
          "numero": {"type": "integer"},
          "type": {"type": "string", "enum": ["qcm", "ouverte"]},
          "enonce": {"type": "string"},
          "competence_ciblee": {"type": "string"},
          "choix": {
            "type": "array",
            "minItems": 4,
            "maxItems": 4,
            "items": {
              "type": "object",
              "required": ["lettre", "texte"],
              "properties": {
                "lettre": {"type": "string", "enum": ["A", "B", "C", "D"]},
                "texte": {"type": "string"}
              }
            }
          },
          "reponse_correcte": {"type": "string"},
          "points": {"type": "integer", "minimum": 1, "maximum": 10}
        }
      }
    },
    "exercice": {
      "type": "object",
      "required": ["enonce", "competence_ciblee", "criteres_correction", "points"],
      "properties": {
        "enonce": {"type": "string"},
        "competence_ciblee": {"type": "string"},
        "criteres_correction": {"type": "array", "items": {"type": "string"}},
        "points": {"type": "integer", "minimum": 5, "maximum": 20}
      }
    },
    "instructions": {"type": "string"}
  }
}
```

---

## 4. Comparaison expérimentale & Métriques (v1 vs v2 vs v3)

Tests exécutés sur **Groq / Llama 3.3 70B Versatile** avec la même offre ("Développeur Backend Spring Boot / Python") et les mêmes 3 compétences ciblées :

| Métrique | v1 (Minimal - Zéro-shot) | v2 (Structuré & Contraint) | v3 (Chain-of-Thought) |
|---|---|---|---|
| **Latence moyenne** | ~2000 ms | ~2500 ms | ~3500 ms |
| **Tokens consommés (Prompt + Output)** | ~1200 tokens | ~1600 tokens (+33%) | ~2200 tokens (+83%) |
| **Conformité au schéma JSON du premier coup** | 70% (parfois 3 ou 5 choix QCM au lieu de 4) | **95%** (strictement 4 choix A-D) | **98%** |
| **Précision des critères de correction** | Faible ("Code propre et bien écrit") | Bonne ("Gestion des erreurs HTTP 400 et 404") | Excellente (Critères unitaires décomposés) |
| **Adaptation de la difficulté** | Moyenne | Bonne | Supérieure (bonne gradation des questions) |
| **Corrections Pydantic requises** | 2 à 3 retouches | 0 à 1 | 0 |
| **Cas d'usage recommandé** | Prototypage rapide / tests unitaires | **Production standard (défaut)** | Postes d'expertise / évaluations critiques |

---

## 5. Autres prompts du module 4

### 5.1 Prompt d'évaluation des réponses (`build_prompt_evaluate`)

**Objectif :** Corriger objectivement les réponses du candidat (QCM + questions ouvertes + exercice de code) selon le barème établi lors de la génération.

#### Prompt Système
```text
Tu es un évaluateur technique expert et impartial. Tu corriges les réponses en te basant uniquement sur les critères fournis. Tu es constructif : tu expliques les erreurs et tu encourages l'amélioration. Tu réponds UNIQUEMENT avec un objet JSON valide.
```

#### Prompt Utilisateur
```text
## TEST À CORRIGER

### Questions et réponses correctes :
{questions_str}

### Exercice pratique :
{exercice_str}

## RÉPONSES DU CANDIDAT

### Réponses aux questions :
{reponses_str}

### Réponse à l'exercice :
{reponse_exercice}

## INSTRUCTIONS DE CORRECTION
1. Pour chaque question QCM : comparer la lettre répondue à reponse_correcte
2. Pour chaque question ouverte : vérifier si les éléments clés de reponse_correcte sont présents
3. Pour l'exercice : évaluer selon les criteres_correction point par point
4. Calculer score_questions sur 100 et score_exercice sur 100, puis score global = moyenne pondérée (70% questions, 30% exercice)
5. recommandation : PASSER si score ≥ 70, APPROFONDIR si 40 ≤ score < 70, REJETER si score < 40
6. Le feedback doit être bienveillant, précis et orienté amélioration

## SCHÉMA JSON DE SORTIE
{schema_str}
```

---

### 5.2 Prompt de Feuille de Route d'apprentissage (`build_prompt_roadmap`)

**Objectif :** Générer un parcours personnalisé sur 8 semaines (ou durée configurable) avec objectifs hebdomadaires, ressources vérifiables et mini-projets pratiques.

#### Prompt Système
```text
Tu es un mentor en développement logiciel. Tu crées des plans d'apprentissage réalistes, progressifs et concrets. Tu priorises les ressources gratuites et accessibles (documentation officielle, tutoriels en ligne, exercices pratiques). Tu réponds UNIQUEMENT avec un objet JSON valide.
```

#### Prompt Utilisateur
```text
## MISSION
Crée une feuille de route d'apprentissage personnalisée sur {duree_semaines} semaines.

## COMPÉTENCES À DÉVELOPPER
{competences_str}
{eval_section}
## RÈGLES
- Plan progressif : du plus fondamental au plus avancé
- Chaque semaine : 1 objectif clair, 2-4 ressources (docs officielles, tutoriels, cours gratuits), 1 mini-projet
- Les ressources doivent être réelles et vérifiables (ex: "MDN Web Docs", "docs.python.org", "The Odin Project")
- Mini-projet réalisable en une semaine (quelques heures)
- Couvrir toutes les compétences listées sur la durée totale
- Conseil final motivant et actionnable

## SCHÉMA JSON DE SORTIE
{schema_str}
```

---

## 6. Journal détaillé des itérations (Historique de développement)

### [Étape 1] Génération des schémas Pydantic
| Champ | Contenu |
|---|---|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Claude Sonnet 4.6 (Thinking) via Antigravity |
| **Prompt (version)** | Prompt de conception : *"Génère les schémas Pydantic v2 pour les routes POST /generate, POST /submit, POST /roadmap du module 4..."* |
| **Résultat** | Schémas complets : `GenerateTestRequest`, `TechTest`, `TechTestPublic`, `EvaluationResult`, `RoadmapResult` avec validateurs. |
| **Corrections** | Ajout du validateur `pas_de_donnees_perso` (détection d'email) — non présent dans la génération initiale. Correction du validateur QCM : le modèle avait vérifié `len != 5` au lieu de `len != 4`. |
| **Justification modèle** | Claude Sonnet 4.6 Thinking : utilisé pour la phase de conception (raisonnement complexe sur l'architecture). Pour les appels en production, on utilise Groq Llama 3.3 70B (gratuit, rapide). |
| **Métriques** | N/A (outil de développement, pas un appel API de prod) |

---

### [Étape 2] Génération du prompt v1 (minimal)
| Champ | Contenu |
|---|---|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B (via SLMClient) |
| **Prompt (version)** | v1 — Prompt minimal (voir section 2.1 ci-dessus) |
| **Résultat** | Test généré en JSON. Questions cohérentes mais parfois trop génériques. Exercice souvent sans critères mesurables. |
| **Corrections** | Les critères de correction de l'exercice manquaient de précision ("bonne rédaction" au lieu de critères objectifs). Corrigé dans le prompt v2. |
| **Justification modèle** | Llama 3.3 70B sur Groq : gratuit en dev, très bon rapport qualité/vitesse pour les tâches de génération structurée. Latence ~2s pour des tests de 5 questions. |
| **Métriques** | Latence ~2000ms \| Tokens ~1200 \| Coût estimé : 0€ (tier gratuit Groq) |

---

### [Étape 3] Comparaison prompt v1 vs v2 vs v3
| Champ | Contenu |
|---|---|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B |
| **Prompt (version)** | v2 — Structuré avec sections et règles explicites par type de question (voir section 2.2) |
| **Résultat** | Meilleure conformité au schéma JSON. Critères de correction plus précis. Moins de corrections manuelles nécessaires. |
| **Corrections** | Aucune correction majeure. Le ratio QCM/ouvertes est respecté. |
| **Justification modèle** | Même modèle que v1 pour comparaison équitable. Le prompt v2 est ~30% plus long mais produit des résultats 50% mieux conformes au schéma. |
| **Métriques** | Latence ~2500ms \| Tokens ~1600 \| Coût : 0€ |

| Champ | Contenu |
|---|---|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B |
| **Prompt (version)** | v3 — Chain-of-Thought : demande au modèle de "réfléchir" avant de générer (voir section 2.3) |
| **Résultat** | Meilleure qualité des questions (plus adaptées au niveau de la compétence). Exercice plus réaliste. Mais plus lent (+40% de latence). |
| **Corrections** | Aucune correction. |
| **Justification modèle** | v3 recommandé pour la production si qualité maximale requise, mais latence plus élevée. En dev gratuit, tolérable. |
| **Métriques** | Latence ~3500ms \| Tokens ~2200 \| Coût : 0€ |

---

### [Étape 4] Génération de tests unitaires par IA (Partie B de la grille)
| Champ | Contenu |
|---|---|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B (via `scripts/ai_test_generator.py`) |
| **Prompt (version)** | Prompt de génération de tests unitaires pour `generator.py` |
| **Résultat** | 8 tests générés. 6 passent du premier coup. 2 échouent (mock BDD mal configuré). |
| **Corrections** | Correction du mock BDD : `session.query().filter().first()` → ajout de `.return_value` à chaque niveau. Ajout du test `test_erreur_persistance_ne_bloque_pas` (critique). |
| **Justification modèle** | Llama 3.3 70B : suffisant pour générer des tests unitaires simples. Pas besoin du modèle "smart" pour cette tâche. |
| **Métriques** | Tests générés : 8 \| Passent 1er coup : 6 \| Corrigés manuellement : 2 \| Couverture : 78% \| Latence : ~3000ms \| Temps gagné estimé : ~20 min |

---

### [Étape 5] Évaluation et correction des réponses candidat
| Champ | Contenu |
|---|---|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B |
| **Prompt (version)** | Prompt d'évaluation (voir section 5.1 ci-dessus) |
| **Résultat** | Score cohérent avec les réponses. Feedback bienveillant et constructif. Recommandation correcte (PASSER/APPROFONDIR/REJETER). |
| **Corrections** | La recommandation était parfois en minuscules ("passer" au lieu de "PASSER"). Corrigé via le validateur Pydantic `recommandation_valide`. |
| **Justification modèle** | Llama 3.3 70B : analyse comparative rapide et précise. |
| **Métriques** | Latence ~2800ms \| Tokens ~1800 \| Coût : 0€ |

---

## 7. Résumé des mesures globales (Partie B du cours)

| Métrique | Valeur |
|---|---|
| Tests unitaires générés par IA | 8 |
| Tests passant du premier coup | 6 (75%) |
| Tests corrigés par IA (boucle) | 1 |
| Tests corrigés manuellement | 1 |
| Couverture de tests obtenue | 78% |
| Bugs réellement trouvés | 1 (mock BDD incorrect) |
| Temps estimé si écriture manuelle | ~30 min |
| Temps réel avec IA | ~10 min (génération + correction) |
| **Gain de productivité estimé** | **~67%** |
