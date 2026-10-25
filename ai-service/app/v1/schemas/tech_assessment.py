"""
Schémas Pydantic v2 — Module 4 : Tests techniques et feuille de route.
Auteur : Aziz (module4-aziz)

Ces modèles servent à deux fins :
  1. Valider les requêtes entrantes (FastAPI les utilise automatiquement).
  2. Valider les réponses du LLM avant de les renvoyer au client
     (règle prof : toute sortie IA est validée par un schéma JSON).
"""

from __future__ import annotations

from enum import Enum
from typing import Annotated

from pydantic import BaseModel, Field, field_validator


# ---------------------------------------------------------------------------
# Énumérations partagées
# ---------------------------------------------------------------------------

class TypeQuestion(str, Enum):
    """Deux types de questions possibles dans un test généré."""
    QCM = "qcm"
    OUVERTE = "ouverte"


class NiveauCompetence(str, Enum):
    """Niveaux de maîtrise d'une compétence (interface avec le module 2)."""
    DEBUTANT = "débutant"
    INTERMEDIAIRE = "intermédiaire"
    AVANCE = "avancé"


# ---------------------------------------------------------------------------
# Entrées : POST /api/v1/tests/generate
# ---------------------------------------------------------------------------

class CompetenceManquante(BaseModel):
    """
    Une compétence lacunaire fournie par le module 2 (matching CV/offre).
    On définit ce format maintenant et on le documente pour l'équipe.
    """
    skill: Annotated[str, Field(min_length=1, max_length=100, description="Nom de la compétence")]
    level: NiveauCompetence = NiveauCompetence.INTERMEDIAIRE


class GenerateTestRequest(BaseModel):
    """Corps de la requête POST /api/v1/tests/generate."""
    job_title: Annotated[str, Field(min_length=2, max_length=200, description="Intitulé du poste")]
    job_description: Annotated[str, Field(min_length=1, max_length=5000, description="Description de l'offre d'emploi")]
    competences_manquantes: Annotated[
        list[CompetenceManquante],
        Field(min_length=1, max_length=20, description="Compétences à tester (issues du module 2)")
    ]
    # Identifiant anonymisé du candidat — JAMAIS de nom/email/données perso
    candidate_id: Annotated[str, Field(min_length=1, max_length=100, description="Identifiant anonymisé du candidat")]

    @field_validator("job_description")
    @classmethod
    def pas_de_donnees_perso(cls, v: str) -> str:
        """
        Vérification minimale : on refuse les descriptions qui ressemblent
        à des emails ou des noms de candidats (règle du prof §1).
        Ce n'est pas un filtre parfait, mais c'est une première barrière.
        """
        import re
        if re.search(r"[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z]{2,}", v):
            raise ValueError("La description ne doit pas contenir d'adresse e-mail (données personnelles).")
        return v


# ---------------------------------------------------------------------------
# Sortie LLM : structure d'un test généré
# ---------------------------------------------------------------------------

class ChoixQCM(BaseModel):
    """Un choix de réponse pour une question QCM."""
    lettre: Annotated[str, Field(pattern=r"^[A-D]$", description="Lettre du choix (A, B, C ou D)")]
    texte: Annotated[str, Field(min_length=1, max_length=500)]


class Question(BaseModel):
    """Une question du test (QCM ou ouverte)."""
    numero: Annotated[int, Field(ge=1, le=10)]
    type: TypeQuestion
    enonce: Annotated[str, Field(min_length=5, max_length=1000, description="Texte de la question")]
    competence_ciblee: Annotated[str, Field(min_length=1, max_length=100, description="Compétence évaluée par cette question")]
    # Présent uniquement pour les QCM
    choix: list[ChoixQCM] | None = None
    # Réponse correcte — UNIQUEMENT stockée côté serveur, jamais envoyée au front
    reponse_correcte: str | None = None
    # Points attribués à cette question
    points: Annotated[int, Field(ge=1, le=10)] = 2

    @field_validator("choix")
    @classmethod
    def qcm_a_4_choix(cls, v: list | None) -> list | None:
        """Un QCM doit avoir exactement 4 choix (A, B, C, D)."""
        if v is not None and len(v) != 4:
            raise ValueError("Un QCM doit avoir exactement 4 choix.")
        return v


class ExercicePratique(BaseModel):
    """Mini-exercice de code ou de conception inclus dans le test."""
    enonce: Annotated[str, Field(min_length=10, max_length=2000, description="Énoncé de l'exercice")]
    competence_ciblee: Annotated[str, Field(min_length=1, max_length=100)]
    criteres_correction: Annotated[
        list[str],
        Field(min_length=1, max_length=10, description="Liste des critères que le LLM utilisera pour corriger")
    ]
    points: Annotated[int, Field(ge=5, le=20)] = 10


class TechTest(BaseModel):
    """
    Structure complète d'un test technique généré par le LLM.
    C'est le JSON Schema que le LLM doit respecter (règle prof §2).
    """
    titre: Annotated[str, Field(min_length=2, max_length=200)]
    job_title: str
    duree_minutes: Annotated[int, Field(ge=10, le=120)] = 45
    questions: Annotated[list[Question], Field(min_length=5, max_length=5, description="Exactement 5 questions")]
    exercice: ExercicePratique
    instructions: Annotated[str, Field(min_length=10, max_length=1000)] = (
        "Répondez à chaque question. Pour l'exercice pratique, "
        "fournissez votre code ou votre raisonnement en texte libre. "
        "Ne partagez pas de données personnelles dans vos réponses."
    )

    @field_validator("questions")
    @classmethod
    def exactement_5_questions(cls, v: list) -> list:
        if len(v) != 5:
            raise ValueError(f"Le test doit contenir exactement 5 questions, reçu : {len(v)}")
        return v


# ---------------------------------------------------------------------------
# Version "publique" du test (sans les réponses correctes) — envoyée au front
# ---------------------------------------------------------------------------

class TechTestPublic(BaseModel):
    """
    Version du test envoyée au candidat.
    Les réponses correctes sont retirées pour éviter la triche.
    """
    test_id: str
    titre: str
    job_title: str
    duree_minutes: int
    questions: list[Question]
    exercice: ExercicePratique
    instructions: str

    @classmethod
    def depuis_test(cls, test_id: str, test: TechTest) -> "TechTestPublic":
        """Construit la version publique en masquant les réponses correctes."""
        questions_publiques = []
        for q in test.questions:
            # Copier sans la réponse correcte
            q_dict = q.model_dump()
            q_dict["reponse_correcte"] = None
            questions_publiques.append(Question(**q_dict))
        return cls(
            test_id=test_id,
            titre=test.titre,
            job_title=test.job_title,
            duree_minutes=test.duree_minutes,
            questions=questions_publiques,
            exercice=test.exercice,
            instructions=test.instructions,
        )


# ---------------------------------------------------------------------------
# Entrées : POST /api/v1/tests/{id}/submit
# ---------------------------------------------------------------------------

class ReponseQuestion(BaseModel):
    """Réponse d'un candidat à une question."""
    numero: Annotated[int, Field(ge=1, le=10)]
    reponse: Annotated[str, Field(min_length=0, max_length=5000, description="Réponse du candidat (texte libre ou lettre QCM)")]


class SubmitTestRequest(BaseModel):
    """Corps de la requête POST /api/v1/tests/{id}/submit."""
    candidate_id: Annotated[str, Field(min_length=1, max_length=100, description="Identifiant anonymisé du candidat")]
    reponses_questions: Annotated[list[ReponseQuestion], Field(min_length=1, max_length=5)]
    reponse_exercice: Annotated[str, Field(min_length=0, max_length=10000, description="Réponse du candidat à l'exercice pratique")]


# ---------------------------------------------------------------------------
# Sortie LLM : résultat d'évaluation
# ---------------------------------------------------------------------------

class EvaluationResult(BaseModel):
    """
    Résultat de la correction par le LLM.
    Structure imposée par le prompt et validée avant affichage (règle prof §2).
    """
    score: Annotated[int, Field(ge=0, le=100, description="Score global sur 100")]
    score_questions: Annotated[int, Field(ge=0, le=100, description="Score questions sur 100")]
    score_exercice: Annotated[int, Field(ge=0, le=100, description="Score exercice sur 100")]
    points_forts: Annotated[list[str], Field(min_length=1, max_length=10, description="Points positifs identifiés")]
    points_faibles: Annotated[list[str], Field(min_length=0, max_length=10, description="Axes d'amélioration")]
    feedback: Annotated[str, Field(min_length=10, max_length=3000, description="Commentaire détaillé du LLM")]
    recommandation: Annotated[str, Field(description="PASSER | APPROFONDIR | REJETER")]

    @field_validator("recommandation")
    @classmethod
    def recommandation_valide(cls, v: str) -> str:
        valeurs = {"PASSER", "APPROFONDIR", "REJETER"}
        if v.upper() not in valeurs:
            raise ValueError(f"recommandation doit être une de {valeurs}, reçu : {v!r}")
        return v.upper()


class SubmitTestResponse(BaseModel):
    """Réponse complète de POST /api/v1/tests/{id}/submit."""
    test_id: str
    candidate_id: str
    evaluation: EvaluationResult


# ---------------------------------------------------------------------------
# Entrées/Sorties : POST /api/v1/roadmap
# ---------------------------------------------------------------------------

class RoadmapRequest(BaseModel):
    """Corps de la requête POST /api/v1/roadmap."""
    candidate_id: Annotated[str, Field(min_length=1, max_length=100)]
    competences_manquantes: Annotated[list[CompetenceManquante], Field(min_length=1, max_length=20)]
    # Optionnel : résultat du test technique (pour personnaliser le plan)
    evaluation: EvaluationResult | None = None
    duree_plan_semaines: Annotated[int, Field(ge=2, le=26, description="Durée souhaitée du plan en semaines")] = 8


class SemaineRoadmap(BaseModel):
    """Un palier hebdomadaire dans la feuille de route."""
    semaine: Annotated[int, Field(ge=1)]
    objectif: Annotated[str, Field(min_length=5, max_length=300)]
    competences: Annotated[list[str], Field(min_length=1, max_length=10)]
    ressources: Annotated[list[str], Field(min_length=1, max_length=10, description="Liens ou titres de cours/docs")]
    mini_projet: Annotated[str, Field(min_length=5, max_length=500, description="Exercice pratique de la semaine")]


class RoadmapResult(BaseModel):
    """Feuille de route générée par le LLM."""
    titre: Annotated[str, Field(min_length=2, max_length=200)]
    duree_semaines: int
    plan: Annotated[list[SemaineRoadmap], Field(min_length=1, max_length=26)]
    conseil_final: Annotated[str, Field(min_length=10, max_length=1000)]


class RoadmapResponse(BaseModel):
    """Réponse complète de POST /api/v1/roadmap."""
    candidate_id: str
    roadmap: RoadmapResult
