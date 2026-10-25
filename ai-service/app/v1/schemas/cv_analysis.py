"""Schémas de l'API d'analyse de CV (Module 1). Contrat Spring <-> FastAPI,
et validation stricte de la sortie du LLM."""

import re
from typing import Literal

from pydantic import BaseModel, Field, field_validator

_PLACEHOLDER = re.compile(r"\[(?:NAME|EMAIL|PHONE|URL|LOCATION|IDENTIFIER|IP|SECRET|TOKEN)\]")


def _clean_text(value: str | None, max_length: int) -> str | None:
    if value is None:
        return None
    # Un marqueur d'anonymisation n'a rien à faire dans un texte affiché au recruteur.
    text = re.sub(r"\s{2,}", " ", _PLACEHOLDER.sub("", value)).strip(" ,;-")
    return text[:max_length] or None


def _clean_list(values: list[str], max_items: int, max_length: int) -> list[str]:
    seen: set[str] = set()
    cleaned: list[str] = []
    for value in values:
        item = " ".join(str(value).split())
        key = item.lower()
        if not item or len(item) > max_length or _PLACEHOLDER.search(item) or key in seen:
            continue
        seen.add(key)
        cleaned.append(item)
    return cleaned[:max_items]


class CvLlmExtraction(BaseModel):
    """Sortie attendue du LLM. Tout champ invalide fait échouer la validation (nouvel essai, puis secours)."""

    headline: str | None = None
    summary: str | None = None
    years_of_experience: int | None = Field(default=None, ge=0, le=60)
    skills: list[str] = Field(default_factory=list)
    soft_skills: list[str] = Field(default_factory=list)
    languages: list[str] = Field(default_factory=list)

    @field_validator("headline")
    @classmethod
    def clean_headline(cls, value: str | None) -> str | None:
        return _clean_text(value, 150)

    @field_validator("summary")
    @classmethod
    def clean_summary(cls, value: str | None) -> str | None:
        return _clean_text(value, 1000)

    @field_validator("skills")
    @classmethod
    def clean_skills(cls, value: list[str]) -> list[str]:
        return _clean_list(value, max_items=50, max_length=80)

    @field_validator("soft_skills")
    @classmethod
    def clean_soft_skills(cls, value: list[str]) -> list[str]:
        return _clean_list(value, max_items=15, max_length=80)

    @field_validator("languages")
    @classmethod
    def clean_languages(cls, value: list[str]) -> list[str]:
        return _clean_list(value, max_items=10, max_length=50)


class CvAnonymizationReport(BaseModel):
    replaced_count: int = Field(ge=0, description="Nombre de données personnelles masquées")
    categories: list[str] = Field(description="Catégories masquées (EMAIL, PHONE, NAME, LOCATION, URL...)")


class NormalizedSkillDetail(BaseModel):
    original: str = Field(description="Compétence telle qu'extraite du CV", examples=["ReactJS"])
    name: str = Field(description="Nom normalisé", examples=["React"])
    category: str | None = Field(description="null si absente du référentiel", examples=["FRAMEWORK"])
    method: Literal["EXACT", "VERSION", "FUZZY", "UNKNOWN"]
    score: int = Field(ge=0, le=100)


class CvProfileProposal(BaseModel):
    """Proposition de l'IA : le candidat la vérifie et la valide avant tout enregistrement."""
    headline: str | None
    summary: str | None
    years_of_experience: int | None
    experience_level: Literal["JUNIOR", "INTERMEDIATE", "SENIOR"] | None = Field(
        description="Calculé par la règle métier à partir des années, pas par le LLM")
    skills: list[str] = Field(description="Compétences normalisées et dédoublonnées")
    skills_details: list[NormalizedSkillDetail]
    skills_referential_version: str = Field(examples=["skills-ref-v1"])
    soft_skills: list[str]
    languages: list[str]


class LlmAttemptTrace(BaseModel):
    model: str
    error_code: str | None = Field(description="null si la tentative a réussi")
    latency_ms: int | None = None


class LlmTrace(BaseModel):
    """Traçabilité : quel modèle a répondu, avec quel prompt, après quelles tentatives."""
    model: str = Field(examples=["gemini-3.5-flash"])
    fallback_used: bool
    prompt_version: str = Field(examples=["cv-extract-v1"])
    attempts: list[LlmAttemptTrace]
    input_tokens: int | None = None
    output_tokens: int | None = None


class CvAnalysisResponse(BaseModel):
    pipeline_version: str = Field(examples=["cv-pipeline-v3"])
    page_count: int = Field(ge=1)
    text_chars: int = Field(ge=0, description="Longueur du texte extrait (après troncature éventuelle)")
    truncated: bool = Field(description="True si le texte a été tronqué avant les étapes suivantes")
    anonymization: CvAnonymizationReport
    anonymized_text: str = Field(description="Texte anonymisé. Le texte brut n'est jamais renvoyé.")
    profile: CvProfileProposal
    llm: LlmTrace
    durations_ms: dict[str, int] = Field(description="Durée de chaque étape, en millisecondes")


class CvAnalysisError(BaseModel):
    code: str = Field(examples=["NOT_PDF"])
    detail: str
