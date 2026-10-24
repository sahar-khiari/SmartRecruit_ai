from typing import Annotated

from fastapi import APIRouter, Depends

from app.core.security import require_internal_api_key
from app.models.retrieval import RetrievalSearchRequest, RetrievalSearchResponse
from app.services.retrieval_service import RetrievalService, get_retrieval_service

router = APIRouter(
    prefix="/api/v1/retrieval",
    tags=["Recherche sémantique interne"],
    dependencies=[Depends(require_internal_api_key)],
)


@router.post(
    "/search",
    response_model=RetrievalSearchResponse,
    summary="Rechercher des chunks RH pertinents",
    description=(
        "Endpoint interne de diagnostic appelé par Spring Boot. Retourne uniquement "
        "des chunks classés par similarité cosinus. Aucune réponse conversationnelle "
        "n'est générée et aucun LLM n'est appelé. Le score est une similarité "
        "vectorielle, pas une probabilité."
    ),
)
def search(
    request: RetrievalSearchRequest,
    service: Annotated[RetrievalService, Depends(get_retrieval_service)],
) -> RetrievalSearchResponse:
    return service.search(request)
