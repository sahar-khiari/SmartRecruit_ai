from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api import chat, health, knowledge, retrieval
from app.core.config import get_settings
from app.core.logging_config import configure_logging
from app.services.ollama_client import (
    OllamaOutputError,
    OllamaTimeoutError,
    OllamaUnavailableError,
)

logger = logging.getLogger(__name__)

DESCRIPTION = """
Service interne RAG local pour la base de connaissances RH de SmartRecruit AI.

**Périmètre**
- Recherche par embeddings locaux dans ChromaDB.
- Génération fondée sur les chunks récupérés avec qwen3:4b via Ollama local.
- Sortie JSON structurée validée par Pydantic, sources reconstruites depuis le retrieval.
- MariaDB reste la source de vérité documentaire ; ChromaDB n'est qu'un index reconstructible.
- Angular ne doit jamais appeler ce service directement :
  `Angular → Spring Boot → FastAPI → Ollama`.

**Sécurité**
Les endpoints `/api/v1/**` exigent le header `X-AI-Service-Key`.
`/health` reste public pour la supervision locale et n'expose aucun secret.
Aucun service cloud ni clé API externe n'est utilisé.
"""


def create_app() -> FastAPI:
    settings = get_settings()
    configure_logging(settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        # Aucun chargement de modèle au démarrage : /health et la première
        # indexation déclenchent un chargement paresseux.
        yield

    application = FastAPI(
        title="SmartRecruit AI Service",
        version="1.1.0",
        description=DESCRIPTION,
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        lifespan=lifespan,
    )

    application.include_router(health.router)
    application.include_router(knowledge.router)
    application.include_router(retrieval.router)
    application.include_router(chat.router)

    @application.exception_handler(ValueError)
    async def value_error_handler(_: Request, exc: ValueError) -> JSONResponse:
        return JSONResponse(status_code=422, content={"detail": str(exc)})

    @application.exception_handler(OllamaTimeoutError)
    async def ollama_timeout_handler(
        _: Request, exc: OllamaTimeoutError
    ) -> JSONResponse:
        logger.warning("Ollama timeout | error=%s", exc.error_code)
        return JSONResponse(
            status_code=504,
            content={"detail": "La génération a dépassé le délai autorisé."},
        )

    @application.exception_handler(OllamaUnavailableError)
    async def ollama_unavailable_handler(
        _: Request, exc: OllamaUnavailableError
    ) -> JSONResponse:
        logger.warning("Ollama indisponible | error=%s", exc.error_code)
        return JSONResponse(
            status_code=503,
            content={"detail": "Le modèle local n'est pas disponible."},
        )

    @application.exception_handler(OllamaOutputError)
    async def ollama_output_handler(
        _: Request, exc: OllamaOutputError
    ) -> JSONResponse:
        logger.warning("Sortie Ollama invalide | error=%s", exc.error_code)
        return JSONResponse(
            status_code=502,
            content={"detail": "La réponse du modèle local est invalide."},
        )

    return application


app = create_app()
