"""Appel d'un LLM avec sortie JSON validée, nouvel essai et modèle de secours.

Partagé par l'extraction du CV et le Coach CV. Stratégie, pour chaque modèle dans l'ordre
(principal puis secours) :
- erreur passagère (réseau, 503, délai dépassé) ou JSON invalide : nouvel essai sur le même modèle ;
- quota dépassé, requête refusée, essais épuisés : bascule sur le modèle suivant ;
- clé absente ou refusée : arrêt immédiat (le secours utilise la même clé).
Chaque tentative est tracée (modèle, code d'erreur, durée).
"""

import logging
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any, Generic, TypeVar

from app.coreAgents.Agents.cv_analysis.llm.base import LlmClient, LlmError

logger = logging.getLogger(__name__)

T = TypeVar("T")


@dataclass(frozen=True)
class LlmAttempt:
    model: str
    error_code: str | None
    latency_ms: int | None = None


@dataclass(frozen=True)
class LlmRunOutcome(Generic[T]):
    data: T
    model: str
    fallback_used: bool
    attempts: tuple[LlmAttempt, ...]
    input_tokens: int | None
    output_tokens: int | None


@dataclass
class LlmRunError(Exception):
    code: str
    message: str
    attempts: tuple[LlmAttempt, ...] = field(default_factory=tuple)

    def __str__(self) -> str:
        return self.message


def run_json(clients: list[LlmClient], max_retries: int, system: str, prompt: str,
             schema: dict[str, Any], parse: Callable[[str, str], T], task: str) -> LlmRunOutcome[T]:
    """`parse(texte, modèle)` valide la réponse et lève LlmOutputError si elle est invalide."""
    if not clients:
        raise ValueError("Au moins un client LLM est requis")
    attempts: list[LlmAttempt] = []
    last_error: LlmError | None = None

    for index, client in enumerate(clients):
        for _ in range(1 + max_retries):
            try:
                result = client.generate_json(system, prompt, schema)
                data = parse(result.text, client.model)
            except LlmError as error:
                last_error = error
                attempts.append(LlmAttempt(client.model, error.code))
                logger.warning("%s en échec | model=%s code=%s", task, client.model, error.code)
                if not error.fallback_allowed:
                    raise LlmRunError(error.code, str(error), tuple(attempts)) from error
                if not error.retryable:
                    break
                continue

            attempts.append(LlmAttempt(client.model, None, result.latency_ms))
            return LlmRunOutcome(data, client.model, index > 0, tuple(attempts),
                                 result.input_tokens, result.output_tokens)

    raise LlmRunError(
        "LLM_UNAVAILABLE",
        "Aucun modèle n'a pu traiter le CV. Veuillez réessayer plus tard.",
        tuple(attempts),
    ) from last_error
