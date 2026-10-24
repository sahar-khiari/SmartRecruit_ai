"""
Prompts LLM — Module 4 : Génération de tests techniques.
Auteur : Aziz (module4-aziz)

3 versions de prompt pour le prompt tuning (exigence de notation) :
  - v1 : prompt minimal (référence)
  - v2 : prompt structuré avec exemples (few-shot)
  - v3 : prompt avec chaîne de pensée (chain-of-thought)

Les métriques (latence, tokens, qualité) sont comparées et documentées
dans docs/prompts-aziz.md.

⚠️  Règle sécurité : ces prompts ne reçoivent JAMAIS de données personnelles
(nom, email, CV brut). Seuls le titre du poste, la description anonymisée
et la liste de compétences y sont injectés.
"""

import json
from typing import Literal

from app.v1.schemas.tech_assessment import CompetenceManquante

# Type alias pour identifier la version de prompt (utile pour les métriques)
PromptVersion = Literal["v1", "v2", "v3"]


# ---------------------------------------------------------------------------
# Schéma JSON attendu en sortie du LLM — injecté dans le prompt
# (règle prof §2 : toute sortie IA est validée par un schéma)
# ---------------------------------------------------------------------------

OUTPUT_SCHEMA_GENERATE = {
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
                                "texte": {"type": "string"},
                            }
                        }
                    },
                    "reponse_correcte": {"type": "string"},
                    "points": {"type": "integer", "minimum": 1, "maximum": 10},
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
                "points": {"type": "integer", "minimum": 5, "maximum": 20},
            }
        },
        "instructions": {"type": "string"},
    }
}

OUTPUT_SCHEMA_EVALUATE = {
    "type": "object",
    "required": ["score", "score_questions", "score_exercice", "points_forts", "points_faibles", "feedback", "recommandation"],
    "properties": {
        "score": {"type": "integer", "minimum": 0, "maximum": 100},
        "score_questions": {"type": "integer", "minimum": 0, "maximum": 100},
        "score_exercice": {"type": "integer", "minimum": 0, "maximum": 100},
        "points_forts": {"type": "array", "items": {"type": "string"}},
        "points_faibles": {"type": "array", "items": {"type": "string"}},
        "feedback": {"type": "string"},
        "recommandation": {"type": "string", "enum": ["PASSER", "APPROFONDIR", "REJETER"]},
    }
}

OUTPUT_SCHEMA_ROADMAP = {
    "type": "object",
    "required": ["titre", "duree_semaines", "plan", "conseil_final"],
    "properties": {
        "titre": {"type": "string"},
        "duree_semaines": {"type": "integer"},
        "plan": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["semaine", "objectif", "competences", "ressources", "mini_projet"],
                "properties": {
                    "semaine": {"type": "integer"},
                    "objectif": {"type": "string"},
                    "competences": {"type": "array", "items": {"type": "string"}},
                    "ressources": {"type": "array", "items": {"type": "string"}},
                    "mini_projet": {"type": "string"},
                }
            }
        },
        "conseil_final": {"type": "string"},
    }
}


# ---------------------------------------------------------------------------
# Helpers internes
# ---------------------------------------------------------------------------

def _liste_competences(competences: list[CompetenceManquante]) -> str:
    """Formate la liste de compétences pour l'injection dans le prompt."""
    return "\n".join(
        f"  - {c.skill} (niveau : {c.level.value})"
        for c in competences
    )


# ---------------------------------------------------------------------------
# VERSION 1 — Prompt minimal (référence de base)
# Avantages : court, peu de tokens, rapide
# Inconvénients : pas de contrainte forte sur le format, risque de drift
# ---------------------------------------------------------------------------

def build_prompt_generate_v1(
    job_title: str,
    job_description: str,
    competences: list[CompetenceManquante],
) -> list[dict]:
    """
    Prompt v1 : instruction directe, sans exemple.
    Sert de référence pour mesurer l'impact des versions améliorées.
    """
    competences_str = _liste_competences(competences)
    schema_str = json.dumps(OUTPUT_SCHEMA_GENERATE, ensure_ascii=False, indent=2)

    system = (
        "Tu es un expert en recrutement IT. "
        "Tu génères des tests techniques objectifs et adaptés au poste. "
        "Tu réponds UNIQUEMENT avec un objet JSON valide, sans texte supplémentaire."
    )

    user = f"""Génère un test technique pour le poste suivant.

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
{schema_str}"""

    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]


# ---------------------------------------------------------------------------
# VERSION 2 — Prompt structuré avec contraintes explicites (recommended)
# Avantages : meilleur respect du format, moins de corrections manuelles
# Inconvénients : plus de tokens (~30% de plus vs v1)
# ---------------------------------------------------------------------------

def build_prompt_generate_v2(
    job_title: str,
    job_description: str,
    competences: list[CompetenceManquante],
) -> list[dict]:
    """
    Prompt v2 : structure en sections + règles explicites par type de question.
    Version recommandée pour la production.
    """
    competences_str = _liste_competences(competences)
    schema_str = json.dumps(OUTPUT_SCHEMA_GENERATE, ensure_ascii=False, indent=2)

    system = (
        "Tu es un expert en évaluation technique pour le recrutement IT. "
        "Tu crées des tests équilibrés, pertinents et non-discriminatoires. "
        "Tes questions sont claires, précises et adaptées au niveau demandé. "
        "Tu réponds UNIQUEMENT avec un objet JSON valide, aucun texte avant ou après."
    )

    user = f"""## MISSION
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
{schema_str}"""

    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]


# ---------------------------------------------------------------------------
# VERSION 3 — Prompt avec chaîne de pensée (Chain-of-Thought)
# Avantages : meilleure qualité des questions, plus de cohérence
# Inconvénients : beaucoup plus de tokens, plus lent (modèle smart requis)
# ---------------------------------------------------------------------------

def build_prompt_generate_v3(
    job_title: str,
    job_description: str,
    competences: list[CompetenceManquante],
) -> list[dict]:
    """
    Prompt v3 : demande au modèle de réfléchir étape par étape avant de générer.
    À utiliser seulement avec le profil 'smart' (coûteux mais de meilleure qualité).
    """
    competences_str = _liste_competences(competences)
    schema_str = json.dumps(OUTPUT_SCHEMA_GENERATE, ensure_ascii=False, indent=2)

    system = (
        "Tu es un expert senior en ingénierie logicielle et en évaluation technique. "
        "Tu conçois des tests qui mesurent précisément les compétences pratiques. "
        "Avant de générer le test, tu analyses le poste et les lacunes pour choisir "
        "les meilleures questions. Tu réponds UNIQUEMENT avec un objet JSON valide."
    )

    user = f"""## CONTEXTE
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

Génère maintenant le test en JSON pur, sans commentaire ni introduction."""

    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]


# ---------------------------------------------------------------------------
# Prompts d'ÉVALUATION (correction des réponses du candidat)
# ---------------------------------------------------------------------------

def build_prompt_evaluate(
    test_data: dict,
    reponses_questions: list[dict],
    reponse_exercice: str,
) -> list[dict]:
    """
    Prompt de correction : le LLM compare les réponses du candidat
    aux réponses correctes et génère un bilan structuré.

    ⚠️ On ne passe PAS le nom du candidat au LLM (anonymisation).
    Le candidate_id numérique reste dans notre base, pas dans le prompt.
    """
    questions_str = json.dumps(test_data.get("questions", []), ensure_ascii=False, indent=2)
    exercice_str = json.dumps(test_data.get("exercice", {}), ensure_ascii=False, indent=2)
    reponses_str = json.dumps(reponses_questions, ensure_ascii=False, indent=2)
    schema_str = json.dumps(OUTPUT_SCHEMA_EVALUATE, ensure_ascii=False, indent=2)

    system = (
        "Tu es un évaluateur technique expert et impartial. "
        "Tu corriges les réponses en te basant uniquement sur les critères fournis. "
        "Tu es constructif : tu expliques les erreurs et tu encourages l'amélioration. "
        "Tu réponds UNIQUEMENT avec un objet JSON valide."
    )

    user = f"""## TEST À CORRIGER

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
{schema_str}"""

    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]


# ---------------------------------------------------------------------------
# Prompt FEUILLE DE ROUTE
# ---------------------------------------------------------------------------

def build_prompt_roadmap(
    competences: list[CompetenceManquante],
    evaluation_summary: str | None,
    duree_semaines: int,
) -> list[dict]:
    """
    Prompt pour générer un plan d'apprentissage personnalisé.
    Le résumé d'évaluation (s'il existe) est anonymisé (pas de données perso).
    """
    competences_str = _liste_competences(competences)
    schema_str = json.dumps(OUTPUT_SCHEMA_ROADMAP, ensure_ascii=False, indent=2)

    eval_section = ""
    if evaluation_summary:
        eval_section = f"\n## RÉSULTAT DU TEST TECHNIQUE\n{evaluation_summary}\n"

    system = (
        "Tu es un mentor en développement logiciel. "
        "Tu crées des plans d'apprentissage réalistes, progressifs et concrets. "
        "Tu priorises les ressources gratuites et accessibles (documentation officielle, "
        "tutoriels en ligne, exercices pratiques). "
        "Tu réponds UNIQUEMENT avec un objet JSON valide."
    )

    user = f"""## MISSION
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
{schema_str}"""

    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]


# ---------------------------------------------------------------------------
# Sélecteur de version de prompt (pour le A/B testing)
# ---------------------------------------------------------------------------

PROMPT_BUILDERS_GENERATE = {
    "v1": build_prompt_generate_v1,
    "v2": build_prompt_generate_v2,
    "v3": build_prompt_generate_v3,
}


def get_generate_messages(
    job_title: str,
    job_description: str,
    competences: list[CompetenceManquante],
    version: PromptVersion = "v2",
) -> list[dict]:
    """Retourne les messages pour la version de prompt demandée."""
    builder = PROMPT_BUILDERS_GENERATE.get(version, build_prompt_generate_v2)
    return builder(job_title, job_description, competences)
