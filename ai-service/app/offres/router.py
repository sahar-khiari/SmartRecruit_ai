"""Router FastAPI - Module 2 : IA pour la gestion des offres (Cohere).

Expose (protégé par la clé interne X-AI-Service-Key, appelé uniquement par Spring Boot) :
  POST /api/v1/offres/ia/description
  POST /api/v1/offres/ia/competences
  POST /api/v1/offres/ia/mots-cles
"""
from typing import Annotated, Callable, TypeVar

from fastapi import APIRouter, Depends, HTTPException

from app.core.security import require_internal_api_key
from app.offres.cohere_client import CohereError
from app.offres.schemas import (
    CompetencesRequest,
    CompetencesResponse,
    DescriptionRequest,
    DescriptionResponse,
    MotsClesRequest,
    MotsClesResponse,
)
from app.offres.service import OffresIaService, get_offres_service

R = TypeVar("R")

router = APIRouter(
    prefix="/api/v1/offres/ia",
    tags=["Module 2 - IA des offres (Cohere)"],
    dependencies=[Depends(require_internal_api_key)],
)

Service = Annotated[OffresIaService, Depends(get_offres_service)]


def _executer(action: Callable[[], R]) -> R:
    """Traduit les erreurs Cohere en réponses HTTP claires (sans détail technique ni secret)."""
    try:
        return action()
    except CohereError as exc:
        raise HTTPException(
            status_code=exc.status_code,
            detail={"code": exc.error_code, "message": exc.message},
        ) from exc


@router.post("/description", response_model=DescriptionResponse, response_model_by_alias=True)
def generer_description(request: DescriptionRequest, service: Service) -> DescriptionResponse:
    return _executer(lambda: service.generer_description(request))


@router.post("/competences", response_model=CompetencesResponse, response_model_by_alias=True)
def suggerer_competences(request: CompetencesRequest, service: Service) -> CompetencesResponse:
    return _executer(lambda: service.suggerer_competences(request))


@router.post("/mots-cles", response_model=MotsClesResponse, response_model_by_alias=True)
def detecter_mots_cles(request: MotsClesRequest, service: Service) -> MotsClesResponse:
    return _executer(lambda: service.detecter_mots_cles(request))
