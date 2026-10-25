"""
Modèles Pydantic pour le module matching.
Toutes les entrées et sorties sont validées strictement.
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field, field_validator, RootModel


# ── Requête embed-rank (Layer 1) ─────────────────────────────────────────────

class CandidateEntry(BaseModel):
    """Entrée : identifiant + texte CV anonymisé."""
    candidate_id: int
    cv_text: str = Field(..., max_length=60_000)


class EmbedRankRequest(BaseModel):
    """Requête de classement par embeddings sémantiques."""
    offre_texte: str = Field(..., max_length=10_000)
    candidates: List[CandidateEntry] = Field(..., max_length=200)


class EmbedRankResult(BaseModel):
    candidate_id: int
    score: float  # similarité cosinus [0, 1]
    engine: str = "EMBEDDINGS"


class EmbedRankResponse(BaseModel):
    available: bool
    results: List[EmbedRankResult] = []
    error_message: Optional[str] = None


# ── Requête LLM-judge (Layer 2) ──────────────────────────────────────────────

class LlmJudgeRequest(BaseModel):
    """
    Requête d'évaluation LLM d'un CV face à une offre.
    Le cv_text_anonymise NE doit PAS contenir de données personnelles brutes.
    """
    cv_text_anonymise: str = Field(..., max_length=20_000)
    offre_texte: str = Field(..., max_length=10_000)
    niveau_requis: str = Field(default="INTERMEDIAIRE", max_length=30)
    criteres_noms: List[str] = Field(default_factory=list, max_length=20)
    prompt_version: str = Field(default="matching-v1", max_length=30)


class CriteriaScores(RootModel[Dict[str, int]]):
    """Score par critère (0–100)."""
    pass


class LlmJudgeResponse(BaseModel):
    """Résultat structuré du LLM-judge, validé par Pydantic."""
    available: bool
    criteria_scores: Optional[Dict[str, int]] = None
    evidence_quotes: Optional[List[str]] = None
    missing_skills: Optional[List[str]] = None
    seniority_fit: Optional[str] = None  # JUNIOR|INTERMEDIAIRE|SENIOR|EXPERT
    verdict: Optional[str] = None  # STRONG_MATCH|POSSIBLE|WEAK
    llm_score: float = 0.0  # score global normalisé [0,1]
    explication: Optional[str] = None
    error_message: Optional[str] = None

    @field_validator("verdict")
    @classmethod
    def validate_verdict(cls, v: Optional[str]) -> Optional[str]:
        if v and v not in {"STRONG_MATCH", "POSSIBLE", "WEAK"}:
            return "POSSIBLE"  # fallback sûr
        return v

    @field_validator("llm_score")
    @classmethod
    def clamp_score(cls, v: float) -> float:
        return max(0.0, min(1.0, float(v)))
