"""Schémas Pydantic du Module 2 : entrées des endpoints et sorties IA validées."""
from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, field_validator


class _CamelModel(BaseModel):
    """Accepte et renvoie les noms de champs en camelCase, comme le backend Spring."""

    model_config = ConfigDict(populate_by_name=True, str_strip_whitespace=True)


# --------------------------------------------------------------------- entrées

class DescriptionRequest(_CamelModel):
    titre_poste: str = Field(alias="titrePoste", min_length=2, max_length=200)
    niveau_requis: str | None = Field(default=None, alias="niveauRequis", max_length=100)
    type_contrat: str | None = Field(default=None, alias="typeContrat", max_length=50)
    localisation: str | None = Field(default=None, max_length=200)
    mode_travail: str | None = Field(default=None, alias="modeTravail", max_length=30)
    entreprise: str | None = Field(default=None, max_length=200)
    competences: str | None = Field(default=None, max_length=1000)
    notes: str | None = Field(default=None, max_length=2000)


class CompetencesRequest(_CamelModel):
    titre_poste: str = Field(alias="titrePoste", min_length=2, max_length=200)
    description: str | None = Field(default=None, max_length=8000)
    niveau_requis: str | None = Field(default=None, alias="niveauRequis", max_length=100)


class MotsClesRequest(_CamelModel):
    titre_poste: str = Field(alias="titrePoste", min_length=2, max_length=200)
    description: str = Field(min_length=20, max_length=8000)


# ---------------------------------------------- sorties attendues du LLM (validation)

class LlmDescription(BaseModel):
    """Format JSON imposé à Cohere pour la description."""

    accroche: str = Field(min_length=10, max_length=600)
    missions: list[str] = Field(min_length=3, max_length=8)
    profil: list[str] = Field(min_length=3, max_length=8)
    avantages: list[str] = Field(default_factory=list, max_length=6)

    @field_validator("missions", "profil", "avantages")
    @classmethod
    def _non_vides(cls, values: list[str]) -> list[str]:
        cleaned = [v.strip() for v in values if isinstance(v, str) and v.strip()]
        return [v[:300] for v in cleaned]


class LlmCompetences(BaseModel):
    competences: list[str] = Field(min_length=1, max_length=15)


class LlmMotCle(BaseModel):
    mot: str = Field(min_length=2, max_length=60)
    importance: int = Field(ge=1, le=5)


class LlmMotsCles(BaseModel):
    mots_cles: list[LlmMotCle] = Field(min_length=1, max_length=15)


# --------------------------------------------------------------------- sorties

class DescriptionResponse(_CamelModel):
    description: str
    accroche: str
    missions: list[str]
    profil: list[str]
    avantages: list[str]
    modele: str
    version_prompt: str = Field(serialization_alias="versionPrompt")
    depuis_cache: bool = Field(serialization_alias="depuisCache")


class CompetenceSuggeree(_CamelModel):
    nom: str
    categorie: str
    score: float = Field(ge=0.0, le=1.0)
    source: str  # "embedding+llm", "embedding" ou "llm"


class CompetencesResponse(_CamelModel):
    competences: list[CompetenceSuggeree]
    methode: str
    modele: str
    depuis_cache: bool = Field(serialization_alias="depuisCache")


class MotCle(_CamelModel):
    mot: str
    importance: int


class MotsClesResponse(_CamelModel):
    mots_cles: list[MotCle] = Field(serialization_alias="motsCles")
    mots_cles_baseline: list[str] = Field(serialization_alias="motsClesBaseline")
    modele: str
    depuis_cache: bool = Field(serialization_alias="depuisCache")
