"""
Routeur FastAPI du module matching.

Endpoints :
  POST /api/v1/matching/embed-rank  — Layer 1 : classement par embeddings
  POST /api/v1/matching/llm-judge   — Layer 2 : LLM-as-judge

Protection par X-AI-Service-Key (même mécanisme que les autres modules).
Enregistrement défensif : si ce module échoue à l'import, main.py le logue et continue.
"""
from __future__ import annotations

import logging
from typing import Annotated

import numpy as np
from fastapi import APIRouter, Depends, HTTPException

from app.core.security import require_internal_api_key
from app.matching.config import get_matching_settings
from app.matching.models import (
    EmbedRankRequest,
    EmbedRankResponse,
    EmbedRankResult,
    LlmJudgeRequest,
    LlmJudgeResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/v1/matching",
    tags=["Matching"],
    dependencies=[Depends(require_internal_api_key)],
)


@router.post("/embed-rank", response_model=EmbedRankResponse)
async def embed_rank(request: EmbedRankRequest) -> EmbedRankResponse:
    """
    Layer 1 : classe les candidats par similarité sémantique (embeddings).
    Si sentence-transformers n'est pas disponible, retourne available=False
    pour que Spring bascule sur la Layer 0.
    """
    settings = get_matching_settings()

    from app.matching.embedding_service import embed_texts, cosine_similarity_batch

    texts = [request.offre_texte] + [c.cv_text for c in request.candidates]
    ok, embeddings = embed_texts(texts, settings.matching_embedding_model)

    if not ok or embeddings is None:
        logger.info("[Matching] Embeddings indisponibles — layer 1 non disponible")
        return EmbedRankResponse(
            available=False,
            error_message="Modèle d'embedding indisponible",
        )

    offre_vec = embeddings[0]
    candidate_vecs = embeddings[1:]

    scores = cosine_similarity_batch(offre_vec, candidate_vecs)

    results = [
        EmbedRankResult(
            candidate_id=c.candidate_id,
            score=float(scores[i]),
            engine="EMBEDDINGS",
        )
        for i, c in enumerate(request.candidates)
    ]

    # Tri par score décroissant
    results.sort(key=lambda r: r.score, reverse=True)

    logger.debug("[Matching] embed-rank OK : %d candidats évalués", len(results))
    return EmbedRankResponse(available=True, results=results)


@router.post("/llm-judge", response_model=LlmJudgeResponse)
async def llm_judge(request: LlmJudgeRequest) -> LlmJudgeResponse:
    """
    Layer 2 : évaluation LLM d'un CV face à une offre (LLM-as-judge).
    Retourne available=False si MATCHING_LLM_ENABLED=false ou si LLM indisponible.
    Jamais de données personnelles brutes dans les logs.
    """
    settings = get_matching_settings()

    if not settings.matching_llm_enabled:
        return LlmJudgeResponse(
            available=False,
            error_message="LLM matching désactivé (MATCHING_LLM_ENABLED=false)",
        )

    from app.matching.llm_service import call_llm_judge

    raw = call_llm_judge(
        cv_text_anonymise=request.cv_text_anonymise,
        offre_texte=request.offre_texte,
        niveau_requis=request.niveau_requis,
        criteres_noms=request.criteres_noms,
        base_url=settings.matching_llm_base_url,
        api_key=settings.matching_llm_api_key,
        model=settings.matching_llm_model,
        timeout=settings.matching_llm_timeout,
        max_retries=settings.matching_llm_max_retries,
    )

    if raw is None:
        logger.warning("[Matching LLM] Échec après %d tentative(s)", settings.matching_llm_max_retries + 1)
        return LlmJudgeResponse(
            available=False,
            error_message="LLM indisponible ou réponse invalide",
        )

    try:
        response = LlmJudgeResponse(
            available=True,
            criteria_scores=raw.get("criteria_scores"),
            evidence_quotes=raw.get("evidence_quotes"),
            missing_skills=raw.get("missing_skills"),
            seniority_fit=raw.get("seniority_fit"),
            verdict=raw.get("verdict"),
            llm_score=float(raw.get("llm_score", 0.0)),
            explication=raw.get("explication"),
        )
        logger.info("[Matching LLM] Verdict=%s score=%.2f", response.verdict, response.llm_score)
        return response
    except Exception as exc:
        logger.warning("[Matching LLM] Validation Pydantic échouée : %s", exc)
        return LlmJudgeResponse(
            available=False,
            error_message="Réponse LLM invalide (validation échouée)",
        )
