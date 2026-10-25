"""POST /api/v1/cv/analyze : analyse d'un CV PDF. Appelé uniquement par Spring, avec la clé interne."""

from functools import lru_cache
from typing import Annotated

from fastapi import APIRouter, Depends, File, UploadFile, status
from fastapi.responses import JSONResponse

from app.core.security import require_internal_api_key
from app.coreAgents.Agents.cv_analysis.constants import MAX_PDF_BYTES
from app.coreAgents.Agents.cv_analysis.extractor import CvExtractionError, CvExtractor
from app.coreAgents.Agents.cv_analysis.llm.gemini_client import GeminiClient
from app.coreAgents.Agents.cv_analysis.pdf_reader import PdfReadError
from app.coreAgents.Agents.cv_analysis.pipeline import PIPELINE_VERSION, CvAnalysisPipeline
from app.coreAgents.Agents.cv_analysis.settings import get_cv_settings
from app.v1.schemas.cv_analysis import (
    CvAnalysisError,
    CvAnalysisResponse,
    CvAnonymizationReport,
    CvProfileProposal,
    LlmAttemptTrace,
    LlmTrace,
    NormalizedSkillDetail,
)

router = APIRouter(
    prefix="/cv",
    tags=["Module 1 - Analyse de CV"],
    dependencies=[Depends(require_internal_api_key)],
)


@lru_cache
def get_cv_pipeline() -> CvAnalysisPipeline:
    settings = get_cv_settings()
    clients = [
        GeminiClient(settings.gemini_key_value, model, settings.gemini_base_url,
                     settings.gemini_timeout_seconds, settings.gemini_temperature)
        for model in (settings.gemini_model, settings.gemini_fallback_model)
    ]
    return CvAnalysisPipeline(CvExtractor(clients, settings.llm_max_retries))


def _error(status_code: int, code: str, detail: str) -> JSONResponse:
    return JSONResponse(status_code=status_code, content=CvAnalysisError(code=code, detail=detail).model_dump())


@router.post(
    "/analyze",
    response_model=CvAnalysisResponse,
    responses={
        401: {"description": "Clé interne manquante"},
        403: {"description": "Clé interne invalide"},
        413: {"model": CvAnalysisError, "description": "PDF de plus de 5 Mo"},
        422: {"model": CvAnalysisError, "description": "Fichier vide, pas un PDF, illisible ou sans texte"},
        503: {"model": CvAnalysisError,
              "description": "Aucun modèle disponible (LLM_UNAVAILABLE) ou clé Gemini absente/refusée"},
    },
)
def analyze_cv(
    file: Annotated[UploadFile, File(description="CV au format PDF")],
    pipeline: Annotated[CvAnalysisPipeline, Depends(get_cv_pipeline)],
) -> CvAnalysisResponse | JSONResponse:
    # Fonction synchrone : FastAPI l'exécute dans un thread, sans bloquer la boucle (spaCy, appels HTTP).
    content = file.file.read(MAX_PDF_BYTES + 1)
    try:
        result = pipeline.run(content)
    except PdfReadError as exc:
        status_code = (status.HTTP_413_REQUEST_ENTITY_TOO_LARGE if exc.code == "TOO_LARGE"
                       else status.HTTP_422_UNPROCESSABLE_ENTITY)
        return _error(status_code, exc.code, str(exc))
    except CvExtractionError as exc:
        return _error(status.HTTP_503_SERVICE_UNAVAILABLE, exc.code, exc.message)

    extraction = result.extraction
    data = extraction.data
    return CvAnalysisResponse(
        pipeline_version=PIPELINE_VERSION,
        page_count=result.page_count,
        text_chars=result.text_chars,
        truncated=result.truncated,
        anonymization=CvAnonymizationReport(
            replaced_count=result.replaced_count,
            categories=list(result.categories),
        ),
        anonymized_text=result.anonymized_text,
        profile=CvProfileProposal(
            headline=data.headline,
            summary=data.summary,
            years_of_experience=data.years_of_experience,
            experience_level=result.experience_level,
            skills=[skill.name for skill in result.skills],
            skills_details=[NormalizedSkillDetail(original=s.original, name=s.name, category=s.category,
                                                  method=s.method, score=s.score) for s in result.skills],
            skills_referential_version=result.skills_referential_version,
            soft_skills=data.soft_skills,
            languages=data.languages,
        ),
        llm=LlmTrace(
            model=extraction.model,
            fallback_used=extraction.fallback_used,
            prompt_version=extraction.prompt_version,
            attempts=[LlmAttemptTrace(model=a.model, error_code=a.error_code, latency_ms=a.latency_ms)
                      for a in extraction.attempts],
            input_tokens=extraction.input_tokens,
            output_tokens=extraction.output_tokens,
        ),
        durations_ms=result.durations_ms,
    )
