"""Étape 3 du pipeline : extraction structurée du CV anonymisé par LLM, avec gestion des erreurs d'API.

Stratégie, pour chaque modèle dans l'ordre (principal puis secours) :
- erreur passagère (réseau, 503, délai dépassé) ou JSON invalide : nouvel essai sur le même modèle ;
- quota dépassé, requête refusée, essais épuisés : bascule sur le modèle suivant ;
- clé absente ou refusée : arrêt immédiat (le secours utilise la même clé).
Chaque tentative est tracée (modèle, code d'erreur, durée) pour la traçabilité de l'analyse.
"""

import json
import logging
from dataclasses import dataclass, field

from pydantic import ValidationError

from app.coreAgents.Agents.cv_analysis.llm.base import LlmClient, LlmError, LlmOutputError
from app.coreAgents.Agents.cv_analysis.prompts import (
    ACTIVE_PROMPT_VERSION,
    RESPONSE_SCHEMA,
    build_user_prompt,
    get_prompt,
)
from app.v1.schemas.cv_analysis import CvLlmExtraction

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class LlmAttempt:
    model: str
    error_code: str | None
    latency_ms: int | None = None


@dataclass(frozen=True)
class ExtractionOutcome:
    data: CvLlmExtraction
    model: str
    fallback_used: bool
    attempts: tuple[LlmAttempt, ...]
    input_tokens: int | None
    output_tokens: int | None
    prompt_version: str = ACTIVE_PROMPT_VERSION


@dataclass
class CvExtractionError(Exception):
    code: str
    message: str
    attempts: tuple[LlmAttempt, ...] = field(default_factory=tuple)

    def __str__(self) -> str:
        return self.message


class CvExtractor:

    def __init__(self, clients: list[LlmClient], max_retries: int = 1,
                 prompt_version: str = ACTIVE_PROMPT_VERSION) -> None:
        if not clients:
            raise ValueError("Au moins un client LLM est requis")
        self._clients = clients
        self._max_retries = max_retries
        self._prompt = get_prompt(prompt_version)

    def extract(self, anonymized_text: str) -> ExtractionOutcome:
        prompt = build_user_prompt(anonymized_text, self._prompt.version)
        attempts: list[LlmAttempt] = []
        last_error: LlmError | None = None

        for index, client in enumerate(self._clients):
            for _ in range(1 + self._max_retries):
                try:
                    result = client.generate_json(self._prompt.system, prompt, RESPONSE_SCHEMA)
                    data = self._parse(result.text, client.model)
                except LlmError as error:
                    last_error = error
                    attempts.append(LlmAttempt(client.model, error.code))
                    logger.warning("Extraction en échec | model=%s code=%s", client.model, error.code)
                    if not error.fallback_allowed:
                        raise CvExtractionError(error.code, str(error), tuple(attempts)) from error
                    if not error.retryable:
                        break
                    continue

                attempts.append(LlmAttempt(client.model, None, result.latency_ms))
                return ExtractionOutcome(
                    data=data,
                    model=client.model,
                    fallback_used=index > 0,
                    attempts=tuple(attempts),
                    input_tokens=result.input_tokens,
                    output_tokens=result.output_tokens,
                    prompt_version=self._prompt.version,
                )

        raise CvExtractionError(
            "LLM_UNAVAILABLE",
            "Aucun modèle n'a pu analyser le CV. Veuillez réessayer plus tard.",
            tuple(attempts),
        ) from last_error

    @staticmethod
    def _parse(text: str, model: str) -> CvLlmExtraction:
        try:
            return CvLlmExtraction.model_validate(json.loads(text))
        except (json.JSONDecodeError, ValidationError) as exc:
            # Le contenu n'est pas journalisé : il peut citer le CV.
            raise LlmOutputError(f"JSON invalide ({type(exc).__name__})", model) from exc
