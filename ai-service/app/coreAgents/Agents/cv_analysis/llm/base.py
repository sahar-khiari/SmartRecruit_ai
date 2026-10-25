"""Interface commune des clients LLM et erreurs d'API typées.

Le reste du module ne connaît que `LlmClient` : il ignore quel modèle répond.
Chaque erreur dit si un nouvel essai a un sens (`retryable`) et si basculer sur le modèle
de secours peut aider (`fallback_allowed`).
"""

from dataclasses import dataclass
from typing import Any, Protocol


@dataclass(frozen=True)
class LlmResult:
    text: str
    model: str
    latency_ms: int
    input_tokens: int | None = None
    output_tokens: int | None = None


class LlmClient(Protocol):
    model: str

    def generate_json(self, system: str, prompt: str, schema: dict[str, Any]) -> LlmResult:
        """Renvoie une réponse JSON conforme à `schema` (texte brut, validé ensuite par Pydantic)."""


class LlmError(Exception):
    code = "LLM_ERROR"
    retryable = False
    fallback_allowed = True

    def __init__(self, message: str, model: str | None = None) -> None:
        super().__init__(message)
        self.model = model


class LlmNotConfiguredError(LlmError):
    """Clé absente : aucun modèle de ce fournisseur ne peut répondre."""
    code = "LLM_NOT_CONFIGURED"
    fallback_allowed = False


class LlmAuthError(LlmError):
    """Clé refusée (401/403) : le modèle de secours utilise la même clé, inutile d'essayer."""
    code = "LLM_AUTH"
    fallback_allowed = False


class LlmQuotaError(LlmError):
    """Quota dépassé (429) : réessayer tout de suite ne sert à rien, le secours a son propre quota."""
    code = "LLM_QUOTA"


class LlmRequestError(LlmError):
    """Requête refusée (400) ou modèle introuvable (404) : même erreur au nouvel essai, le secours peut aider."""
    code = "LLM_REQUEST"


class LlmTimeoutError(LlmError):
    code = "LLM_TIMEOUT"
    retryable = True


class LlmUnavailableError(LlmError):
    """Réseau coupé, 5xx, modèle surchargé ou introuvable."""
    code = "LLM_UNAVAILABLE"
    retryable = True


class LlmOutputError(LlmError):
    """Réponse vide, bloquée ou JSON non conforme au schéma."""
    code = "LLM_OUTPUT"
    retryable = True
