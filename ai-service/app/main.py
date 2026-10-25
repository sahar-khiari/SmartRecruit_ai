from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api import chat, health, knowledge, retrieval, tech_assessment
from app.core.config import get_settings
from app.core.logging_config import configure_logging
import app.db.models.tech_assessment  # noqa: F401 (Enregistre les tables pour SQLAlchemy)
from app.db.base import Base
from app.db.session import engine
from app.services.ollama_client import (
    OllamaOutputError,
    OllamaTimeoutError,
    OllamaUnavailableError,
)

logger = logging.getLogger(__name__)

DESCRIPTION = """
Service interne IA pour SmartRecruit AI.

**Périmètre**
- Module 1 : Recherche RAG locale pour la base RH (ChromaDB + Ollama).
- Module 4 : Tests techniques dynamiques, évaluation automatique et feuille de route IA.
"""


def create_app() -> FastAPI:
    settings = get_settings()
    configure_logging(settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        # Initialisation de la BDD pour le Module 4 (SQLite par défaut)
        try:
            Base.metadata.create_all(bind=engine)
            logger.info("Tables BDD Module 4 créées ou vérifiées avec succès.")
        except Exception as exc:
            logger.warning("Initialisation BDD Module 4 : %s", exc)
        yield

    application = FastAPI(
        title="SmartRecruit AI Service",
        version="1.2.0",
        description=DESCRIPTION,
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        lifespan=lifespan,
    )

    # Middleware CORS pour permettre au frontend Angular d'appeler directement le service IA
    application.add_middleware(
        CORSMiddleware,
        allow_origins=["http://localhost:4200", "http://127.0.0.1:4200", "*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Routes Module 1 (RAG / Connaissances RH)
    application.include_router(health.router)
    application.include_router(knowledge.router)
    application.include_router(retrieval.router)
    application.include_router(chat.router)

    # Routes Module 4 (Tests techniques / Aziz)
    application.include_router(tech_assessment.router)

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
