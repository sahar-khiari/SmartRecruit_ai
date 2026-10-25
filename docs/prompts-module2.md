# Journal de prompts — Module 2 (Gestion des offres)

À compléter au fil de l'eau (critère « documentation individuelle », 2 points).
Pour chaque interaction : outil, prompt, résultat, correction, justification.

## A. Prompts du produit (Cohere, version `offres-v1`)

Les prompts complets sont dans `ai-service/app/offres/prompts.py`.

| Fonction | Modèle | Température | Pourquoi ces choix |
|---|---|---|---|
| Description | command-r-08-2024 | 0.4 | Un peu de créativité pour un texte attractif, JSON imposé, aucune invention de salaire ou d'entreprise, consigne anti-discrimination |
| Compétences | embed-multilingual-v3.0 + command-r-08-2024 | 0.0 | Embeddings = pré-sélection rapide et bon marché ; le LLM filtre (moins d'hallucinations car il choisit dans une liste) |
| Mots-clés | command-r-08-2024 | 0.0 | Stabilité du résultat (même offre → mêmes mots-clés) |

Justification du modèle : Cohere est le seul fournisseur non utilisé par l'équipe (Ollama, Groq, Gemini, Mistral déjà pris),
offre une clé d'essai gratuite, un modèle d'embedding multilingue adapté au français et une sortie JSON native.

## B. Interactions avec les assistants IA pendant le développement

| Date | Outil / modèle | Prompt (résumé) | Résultat | Ce que j'ai corrigé | Justification / gain |
|---|---|---|---|---|---|
| 25/10/2026 | Claude Code (Opus) | Analyse du dépôt et des 2 documents du projet, entretien pour définir le périmètre du Module 2 | Plan du module, choix de Cohere, découverte que l'offre de base existait déjà (Aziz) | Décision d'étendre l'existant au lieu de dupliquer | Évite deux tables d'offres au merge |
| 25/10/2026 | Claude Code (Opus) | Règle métier : l’admin peut-il modifier une offre ? | Proposition initiale : oui | **Corrigé par moi** : l’admin ne modifie pas le contenu (il peut seulement supprimer) | Propriété des données, logique métier réelle |
| 25/10/2026 | Claude Code (Opus) | Intégrer le Module 2 dans les pages existantes d’Aziz au lieu de pages séparées, sans toucher à l’admin | Fonctionnalités ajoutées dans job-offer-list, my-offers, create-offer | Décision d’équipe : pas de doublons, pas de conflit sur les routes et menus | Intégration plus simple |
| 25/10/2026 | Claude Code (Opus) | Génération backend (statuts, recherche Specification, modération) + tests d'intégration | Code compilé, 7 tests verts | Faille détectée : un candidat pouvait lire un brouillon via `/api/offres/{id}` → corrigée (404) | Sécurité |
| 25/10/2026 | Claude Code (Opus) | Génération du client Cohere, du service IA et des tests pytest (Cohere simulé) | Gestion clé absente / timeout / quota / JSON invalide | — | Exigences du cours couvertes |
| … | … | … | … | … | … |

## C. Mesures à faire pour le rapport (évaluation)

- Taux de JSON valides au premier essai sur 20 offres (description, compétences, mots-clés).
- Comparaison mots-clés **Cohere vs méthode sans IA** (`motsClesBaseline`) : pertinence notée à la main.
- Latence moyenne par fonctionnalité (visible dans les logs FastAPI : `Cohere /v2/chat | status=200 | 1234 ms`).
- Taux de succès du cache (champ `depuisCache`).
- Variance : même offre générée 5 fois (température 0 vs 0.4).
