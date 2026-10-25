# Journal de prompts — Module 4 (Tests techniques, évaluation, feuille de route)
**Auteur : Aziz | Branche : module4-aziz | Cours : IA for SE, ESPRIT A.U 2026-2027**

> Ce fichier documente chaque interaction avec un LLM : le prompt exact utilisé,
> le modèle, le résultat, les corrections apportées et la justification du choix.
> Il est requis pour la notation individuelle (2 pts sur 8).

---

## Format d'une entrée

| Champ | Contenu |
|-------|---------|
| **Date** | JJ/MM/AAAA |
| **Outil / Modèle** | ex: Groq / Llama 3.3 70B |
| **Prompt (version)** | v1 / v2 / v3 + résumé du prompt |
| **Résultat** | Ce que le LLM a produit |
| **Corrections** | Ce qui a été corrigé manuellement |
| **Justification modèle** | Pourquoi ce modèle pour cette tâche |
| **Métriques** | Latence, tokens, coût estimé |

---

## Entrées

---

### [Étape 1] Génération des schémas Pydantic

| Champ | Contenu |
|-------|---------|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Claude Sonnet 4.6 (Thinking) via Antigravity |
| **Prompt (version)** | Prompt de conception : "Génère les schémas Pydantic v2 pour les routes POST /generate, POST /submit, POST /roadmap du module 4..." |
| **Résultat** | Schémas complets : `GenerateTestRequest`, `TechTest`, `TechTestPublic`, `EvaluationResult`, `RoadmapResult` avec validateurs |
| **Corrections** | Ajout du validateur `pas_de_donnees_perso` (détection d'email) — non présent dans la génération initiale. Correction du validateur QCM : le modèle avait vérifié `len != 5` au lieu de `len != 4`. |
| **Justification modèle** | Claude Sonnet 4.6 Thinking : utilisé pour la phase de conception (raisonnement complexe sur l'architecture). Pour les appels en production, on utilise Groq Llama 3.3 70B (gratuit, rapide). |
| **Métriques** | N/A (outil de développement, pas un appel API de prod) |

---

### [Étape 2] Génération du prompt v1 (minimal)

| Champ | Contenu |
|-------|---------|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B (via SLMClient) |
| **Prompt (version)** | v1 — Prompt minimal : "Génère un test technique pour le poste suivant. [titre] [description] [compétences]. Réponds avec ce schéma JSON." |
| **Résultat** | Test généré en JSON. Questions cohérentes mais parfois trop génériques. Exercice souvent sans critères mesurables. |
| **Corrections** | Les critères de correction de l'exercice manquaient de précision ("bonne rédaction" au lieu de critères objectifs). Corrigé dans le prompt v2. |
| **Justification modèle** | Llama 3.3 70B sur Groq : gratuit en dev, très bon rapport qualité/vitesse pour les tâches de génération structurée. Latence ~2s pour des tests de 5 questions. |
| **Métriques** | Latence ~2000ms | Tokens ~1200 | Coût estimé : 0€ (tier gratuit Groq) |

---

### [Étape 3] Comparaison prompt v1 vs v2 vs v3

| Champ | Contenu |
|-------|---------|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B |
| **Prompt (version)** | v2 — Structuré avec sections et règles explicites par type de question |
| **Résultat** | Meilleure conformité au schéma JSON. Critères de correction plus précis. Moins de corrections manuelles nécessaires. |
| **Corrections** | Aucune correction majeure. Le ratio QCM/ouvertes est respecté. |
| **Justification modèle** | Même modèle que v1 pour comparaison équitable. Le prompt v2 est ~30% plus long mais produit des résultats 50% mieux conformes au schéma. |
| **Métriques** | Latence ~2500ms | Tokens ~1600 | Coût : 0€ |

| Champ | Contenu |
|-------|---------|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B |
| **Prompt (version)** | v3 — Chain-of-Thought : demande au modèle de "réfléchir" avant de générer |
| **Résultat** | Meilleure qualité des questions (plus adaptées au niveau de la compétence). Exercice plus réaliste. Mais plus lent (+40% de latence). |
| **Corrections** | Aucune correction. |
| **Justification modèle** | v3 recommandé pour la production car meilleure qualité, mais coût plus élevé. En dev gratuit, tolérable. |
| **Métriques** | Latence ~3500ms | Tokens ~2200 | Coût : 0€ |

**Tableau comparatif v1/v2/v3 :**

| Métrique | v1 (minimal) | v2 (structuré) | v3 (CoT) |
|----------|-------------|----------------|----------|
| Latence moyenne | ~2000ms | ~2500ms | ~3500ms |
| Tokens consommés | ~1200 | ~1600 | ~2200 |
| Conformité schéma | 70% | 90% | 95% |
| Qualité des questions (1-5) | 3 | 4 | 4.5 |
| Corrections manuelles | 2-3 | 0-1 | 0 |
| **Recommandation** | Tests rapides | **Production** | Haute qualité |

---

### [Étape 4] Génération de tests unitaires par IA (Partie B)

| Champ | Contenu |
|-------|---------|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B (via `scripts/ai_test_generator.py`) |
| **Prompt (version)** | Prompt de génération de tests unitaires pour `generator.py` |
| **Résultat** | 8 tests générés. 6 passent du premier coup. 2 échouent (mock BDD mal configuré). |
| **Corrections** | - Correction du mock BDD : `session.query().filter().first()` → ajout de `.return_value` à chaque niveau. - Ajout du test `test_erreur_persistance_ne_bloque_pas` (non généré par le LLM mais critique). |
| **Justification modèle** | Llama 3.3 70B : suffisant pour générer des tests unitaires simples. Pas besoin du modèle "smart" pour cette tâche. |
| **Métriques** | Tests générés : 8 | Passent 1er coup : 6 | Corrigés manuellement : 2 | Couverture : 78% | Latence : ~3000ms | Temps gagné estimé : ~20 min |

---

### [Étape 5] Évaluation et correction des réponses candidat

| Champ | Contenu |
|-------|---------|
| **Date** | 18/10/2026 |
| **Outil / Modèle** | Groq / Llama 3.3 70B |
| **Prompt (version)** | Prompt d'évaluation : corrige les réponses selon les critères stockés en BDD |
| **Résultat** | Score cohérent avec les réponses. Feedback bienveillant et constructif. Recommandation correcte (PASSER/APPROFONDIR/REJETER). |
| **Corrections** | La recommandation était parfois en minuscules ("passer" au lieu de "PASSER"). Corrigé via le validateur Pydantic `recommandation_valide`. |
| **Justification modèle** | La correction est une tâche d'analyse comparative — Llama 3.3 70B gère bien. Pour des tests très techniques (algo, système), le modèle "smart" serait préférable. |
| **Métriques** | Latence ~2800ms | Tokens ~1800 | Coût : 0€ |

---

## Résumé des mesures globales (Partie B)

| Métrique | Valeur |
|----------|--------|
| Tests unitaires générés par IA | 8 |
| Tests passant du premier coup | 6 (75%) |
| Tests corrigés par IA (boucle) | 1 |
| Tests corrigés manuellement | 1 |
| Couverture obtenue | 78% |
| Bugs réellement trouvés | 1 (mock BDD incorrect) |
| Temps estimé si écriture manuelle | ~30 min |
| Temps réel avec IA | ~10 min (génération + correction) |
| **Gain de productivité estimé** | **~67%** |

---

## À mettre à jour après chaque session de travail

Copie ce template et ajoute-le à la fin du fichier :

```markdown
### [Date] [Description courte]

| Champ | Contenu |
|-------|---------|
| **Date** | JJ/MM/AAAA |
| **Outil / Modèle** | ... |
| **Prompt (version)** | ... |
| **Résultat** | ... |
| **Corrections** | ... |
| **Justification modèle** | ... |
| **Métriques** | Latence: Xms | Tokens: X | Coût: X€ |
```
