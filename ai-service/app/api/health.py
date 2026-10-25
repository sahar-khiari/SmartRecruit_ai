from datetime import datetime, timezone
from typing import Annotated

from fastapi import APIRouter, Depends

from app.core.config import Settings, get_settings
from app.models.health import HealthResponse
from app.services.embedding_service import EmbeddingService, get_embedding_service
from app.services.ollama_client import OllamaClient, get_ollama_client
from app.services.vector_store_service import VectorStoreService, get_vector_store_service

router = APIRouter(tags=["Santé"])


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="État du service interne",
    description=(
        "Vérifie FastAPI, la configuration, l'initialisation du modèle d'embeddings "
        "local, l'accès à ChromaDB, la disponibilité d'Ollama et la présence du modèle "
        "local. Aucun secret, chemin sensible ou variable d'environnement n'est exposé. "
        "Aucune génération LLM complète n'est effectuée."
    ),
)
def health(
    settings: Annotated[Settings, Depends(get_settings)],
    embedding_service: Annotated[EmbeddingService, Depends(get_embedding_service)],
    vector_store: Annotated[VectorStoreService, Depends(get_vector_store_service)],
    ollama_client: Annotated[OllamaClient, Depends(get_ollama_client)],
) -> HealthResponse:
    embedding_available = embedding_service.ensure_loaded()
    chroma_available = vector_store.collection_health()
    ollama_health = ollama_client.health()
    all_available = (
        embedding_available
        and chroma_available
        and ollama_health.available
        and ollama_health.model_available
    )
    return HealthResponse(
        status="UP" if all_available else "DEGRADED",
        service="smartrecruit-ai-service",
        version="1.1.0",
        embeddingModel=settings.embedding_model_name,
        chromaAvailable=chroma_available,
        ollamaAvailable=ollama_health.available,
        llmModel=settings.ollama_model,
        llmModelAvailable=ollama_health.model_available,
        timestamp=datetime.now(timezone.utc),
    )
