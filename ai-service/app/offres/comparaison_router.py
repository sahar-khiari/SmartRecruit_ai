"""Router FastAPI - Module 2 : comparaison IA d'offres pour un candidat (Cohere).

Expose (protégé par la clé interne X-AI-Service-Key, appelé uniquement par Spring Boot) :
  POST /api/v1/offres/ia/comparer
"""
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException

from app.core.security import require_internal_api_key
from app.offres.cohere_client import CohereError
from app.offres.comparaison import (
    ComparaisonRequest,
    ComparaisonResponse,
    ComparaisonService,
    get_comparaison_service,
)

router = APIRouter(
    prefix="/api/v1/offres/ia",
    tags=["Module 2 - IA des offres (Cohere)"],
    dependencies=[Depends(require_internal_api_key)],
)


@router.post("/comparer", response_model=ComparaisonResponse, response_model_by_alias=True)
def comparer_offres(
    request: ComparaisonRequest,
    service: Annotated[ComparaisonService, Depends(get_comparaison_service)],
) -> ComparaisonResponse:
    try:
        return service.comparer(request)
    except CohereError as exc:
        raise HTTPException(
            status_code=exc.status_code,
            detail={"code": exc.error_code, "message": exc.message},
        ) from exc
