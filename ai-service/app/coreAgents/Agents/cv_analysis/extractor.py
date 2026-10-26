"""Étape 3 du pipeline : extraction structurée du CV anonymisé par LLM.

La gestion des erreurs d'API (nouvel essai, modèle de secours, traçabilité) est dans llm/runner.py,
partagée avec le Coach CV.
"""

import json
from dataclasses import dataclass

from pydantic import ValidationError

from app.coreAgents.Agents.cv_analysis.llm.base import LlmClient, LlmOutputError
from app.coreAgents.Agents.cv_analysis.llm.runner import LlmAttempt, LlmRunError, run_json
from app.coreAgents.Agents.cv_analysis.prompts import (
    ACTIVE_PROMPT_VERSION,
    RESPONSE_SCHEMA,
    build_user_prompt,
    get_prompt,
)
from app.v1.schemas.cv_analysis import CvLlmExtraction

# Noms historiques conservés (utilisés par l'endpoint et les tests).
CvExtractionError = LlmRunError
__all__ = ["CvExtractionError", "CvExtractor", "ExtractionOutcome", "LlmAttempt"]


@dataclass(frozen=True)
class ExtractionOutcome:
    data: CvLlmExtraction
    model: str
    fallback_used: bool
    attempts: tuple[LlmAttempt, ...]
    input_tokens: int | None
    output_tokens: int | None
    prompt_version: str = ACTIVE_PROMPT_VERSION


class CvExtractor:

    def __init__(self, clients: list[LlmClient], max_retries: int = 1,
                 prompt_version: str = ACTIVE_PROMPT_VERSION) -> None:
        if not clients:
            raise ValueError("Au moins un client LLM est requis")
        self._clients = clients
        self._max_retries = max_retries
        self._prompt = get_prompt(prompt_version)

    @property
    def clients(self) -> list[LlmClient]:
        return self._clients

    @property
    def max_retries(self) -> int:
        return self._max_retries

    def extract(self, anonymized_text: str) -> ExtractionOutcome:
        outcome = run_json(
            self._clients, self._max_retries, self._prompt.system,
            build_user_prompt(anonymized_text, self._prompt.version),
            RESPONSE_SCHEMA, self._parse, task="Extraction",
        )
        return ExtractionOutcome(outcome.data, outcome.model, outcome.fallback_used, outcome.attempts,
                                 outcome.input_tokens, outcome.output_tokens, self._prompt.version)

    @staticmethod
    def _parse(text: str, model: str) -> CvLlmExtraction:
        try:
            return CvLlmExtraction.model_validate(json.loads(text))
        except (json.JSONDecodeError, ValidationError) as exc:
            # Le contenu n'est pas journalisé : il peut citer le CV.
            raise LlmOutputError(f"JSON invalide ({type(exc).__name__})", model) from exc
