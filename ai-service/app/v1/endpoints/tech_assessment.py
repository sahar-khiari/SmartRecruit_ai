"""
Endpoints FastAPI — Module 4 : Tests techniques, évaluation, feuille de route.
Auteur : Aziz (module4-aziz)

Routes :
  POST /api/v1/tests/generate       → génère un test technique pour un candidat
  POST /api/v1/tests/{id}/submit    → soumet et corrige les réponses du candidat
  POST /api/v1/roadmap              → génère une feuille de route personnalisée

Toutes les erreurs sont capturées et retournées avec un message clair.
Aucune donnée personnelle n'est loguée (règle prof §1).
"""

import logging

from fastapi import APIRouter, HTTPException, Query, status

from app.coreAgents.Agents.tech_assessment.evaluator import (
    TechEvaluationError,
    TechTestEvaluator,
)
from app.coreAgents.Agents.tech_assessment.generator import (
    TechTestGenerationError,
    TechTestGenerator,
)
from app.coreAgents.Agents.tech_assessment.roadmap import (
    RoadmapGenerationError,
    RoadmapGenerator,
)
from app.v1.schemas.tech_assessment import (
    GenerateTestRequest,
    RoadmapRequest,
    RoadmapResponse,
    SubmitTestRequest,
    SubmitTestResponse,
    TechTestPublic,
)

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/tests", tags=["Module 4 — Tests techniques"])


# ---------------------------------------------------------------------------
# POST /api/v1/tests/generate
# ---------------------------------------------------------------------------

@router.post(
    "/generate",
    response_model=TechTestPublic,
    status_code=status.HTTP_201_CREATED,
    summary="Générer un test technique",
    description=(
        "Génère un test de 5 questions + 1 exercice pratique basé sur l'offre d'emploi "
        "et les compétences manquantes du candidat (issues du module 2). "
        "La version retournée ne contient PAS les réponses correctes."
    ),
)
def generate_test(
    body: GenerateTestRequest,
    prompt_version: str = Query(
        default="v2",
        pattern="^v[123]$",
        description="Version du prompt LLM (v1=minimal, v2=structuré, v3=chain-of-thought)",
    ),
) -> TechTestPublic:
    """
    Génère un test technique pour un candidat anonymisé.

    - Le champ `candidate_id` doit être un identifiant anonymisé (jamais nom/email).
    - Le champ `prompt_version` permet de comparer les versions de prompts (pour le rapport).
    """
    logger.info(
        "Génération de test — poste: %s | compétences: %d | prompt: %s",
        body.job_title,
        len(body.competences_manquantes),
        prompt_version,
    )

    generator = TechTestGenerator()
    try:
        _test_complet, test_public, _test_id = generator.generate(
            request=body,
            prompt_version=prompt_version,  # type: ignore[arg-type]
        )
    except TechTestGenerationError as exc:
        logger.warning("Échec génération test: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc

    return test_public


# ---------------------------------------------------------------------------
# POST /api/v1/tests/{test_id}/submit
# ---------------------------------------------------------------------------

@router.post(
    "/{test_id}/submit",
    response_model=SubmitTestResponse,
    status_code=status.HTTP_200_OK,
    summary="Soumettre et corriger les réponses d'un candidat",
    description=(
        "Reçoit les réponses du candidat, les envoie au LLM pour correction "
        "(le code n'est JAMAIS exécuté côté serveur), et retourne le score et le feedback."
    ),
)
def submit_test(
    test_id: str,
    body: SubmitTestRequest,
) -> SubmitTestResponse:
    """
    Soumet les réponses du candidat pour évaluation par l'IA.

    - `test_id` : UUID du test généré par `/generate`
    - Le code soumis par le candidat est évalué par le LLM, jamais exécuté.
    """
    logger.info("Soumission test — test_id: %s", test_id)

    evaluator = TechTestEvaluator()
    try:
        result = evaluator.evaluate(test_id=test_id, request=body)
    except TechEvaluationError as exc:
        logger.warning("Échec évaluation test %s: %s", test_id, exc)
        # 404 si le test n'existe pas, 503 si le LLM est indisponible
        if "introuvable" in str(exc).lower():
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=str(exc),
            ) from exc
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc

    return result


# ---------------------------------------------------------------------------
# POST /api/v1/roadmap  (préfixé séparément)
# ---------------------------------------------------------------------------

roadmap_router = APIRouter(prefix="/roadmap", tags=["Module 4 — Feuille de route"])


@roadmap_router.post(
    "",
    response_model=RoadmapResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Générer une feuille de route d'apprentissage",
    description=(
        "Génère un plan hebdomadaire personnalisé basé sur les lacunes du candidat. "
        "Si une évaluation est fournie, le plan est adapté aux résultats du test."
    ),
)
def generate_roadmap(body: RoadmapRequest) -> RoadmapResponse:
    """
    Génère une feuille de route d'apprentissage personnalisée.

    - `candidate_id` : identifiant anonymisé
    - `competences_manquantes` : liste fournie par le module 2
    - `evaluation` : résultat du test technique (optionnel, améliore la personnalisation)
    """
    logger.info(
        "Génération roadmap — candidat anonymisé | compétences: %d | durée: %d semaines",
        len(body.competences_manquantes),
        body.duree_plan_semaines,
    )

    generator = RoadmapGenerator()
    try:
        result = generator.generate(request=body)
    except RoadmapGenerationError as exc:
        logger.warning("Échec génération roadmap: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc

    return result
