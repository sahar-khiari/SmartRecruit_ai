from typing import Annotated

from fastapi import APIRouter, Depends

from app.core.security import require_internal_api_key
from app.models.chat import RagChatRequest, RagChatResponse
from app.services.chat_service import ChatService, get_chat_service

router = APIRouter(
    prefix="/api/v1/chat",
    tags=["Assistant RH RAG"],
    dependencies=[Depends(require_internal_api_key)],
)


@router.post(
    "/generate",
    response_model=RagChatResponse,
    summary="Générer une réponse RH fondée sur la base de connaissances",
    description=(
        "Endpoint interne appelé exclusivement par Spring Boot. La question est "
        "nettoyée et anonymisée, les chunks pertinents sont récupérés dans ChromaDB, "
        "puis qwen3:4b est appelé localement via Ollama avec un JSON Schema. "
        "Les sources sont reconstruites depuis le retrieval ; le LLM ne peut fournir "
        "que des chunk IDs. Aucune donnée n'est envoyée à un service cloud."
    ),
)
def generate(
    request: RagChatRequest,
    service: Annotated[ChatService, Depends(get_chat_service)],
) -> RagChatResponse:
    return service.generate(request)
