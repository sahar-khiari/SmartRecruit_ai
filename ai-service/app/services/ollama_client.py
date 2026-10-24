import logging
import time
from dataclasses import dataclass
from functools import lru_cache
from typing import Callable, Generic, TypeVar

import httpx
from pydantic import BaseModel, ValidationError

from app.core.config import Settings, get_settings

logger = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseModel)


class OllamaError(Exception):
    """Erreur contrôlée du client Ollama, sans donnée sensible."""

    error_code = "OLLAMA_ERROR"


class OllamaUnavailableError(OllamaError):
    error_code = "OLLAMA_UNAVAILABLE"


class OllamaModelUnavailableError(OllamaUnavailableError):
    error_code = "OLLAMA_MODEL_UNAVAILABLE"


class OllamaTimeoutError(OllamaError):
    error_code = "OLLAMA_TIMEOUT"


class OllamaOutputError(OllamaError):
    error_code = "OLLAMA_INVALID_OUTPUT"


@dataclass(frozen=True)
class OllamaHealth:
    available: bool
    model_available: bool


@dataclass(frozen=True)
class OllamaGenerationResult(Generic[T]):
    output: T
    prompt_token_count: int | None
    completion_token_count: int | None
    duration_ms: int
    attempts: int


class OllamaClient:
    """Client HTTP local pour Ollama.

    Le prompt, la question et les documents ne sont jamais journalisés. Seuls le
    modèle, la durée, le nombre de tokens, le statut et le numéro de tentative
    peuvent apparaître dans les logs techniques.
    """

    def __init__(
        self,
        settings: Settings,
        *,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._settings = settings
        self._client = httpx.Client(
            base_url=settings.ollama_base_url,
            timeout=httpx.Timeout(settings.ollama_timeout_seconds),
            transport=transport,
        )

    def close(self) -> None:
        self._client.close()

    def health(self) -> OllamaHealth:
        """Vérifie le serveur et la présence du modèle sans génération."""
        try:
            response = self._client.get("/api/tags", timeout=3.0)
            response.raise_for_status()
            payload = response.json()
        except (httpx.HTTPError, ValueError):
            return OllamaHealth(available=False, model_available=False)

        models = payload.get("models") if isinstance(payload, dict) else None
        if not isinstance(models, list):
            return OllamaHealth(available=True, model_available=False)

        names = {
            str(model.get("name", "")).strip()
            for model in models
            if isinstance(model, dict)
        }
        model_available = self._settings.ollama_model in names
        return OllamaHealth(available=True, model_available=model_available)

    def generate_structured(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        output_model: type[T],
        validate_output: Callable[[T], None] | None = None,
    ) -> OllamaGenerationResult[T]:
        """Appelle /api/chat avec un JSON Schema et valide la sortie.

        Une seule seconde tentative est autorisée par défaut. Aucune boucle
        infinie n'est possible : le nombre d'appels est borné par
        ``llm_max_retries + 1``.
        """
        schema = output_model.model_json_schema()
        max_attempts = self._settings.llm_max_retries + 1
        last_reason = "réponse vide"

        for attempt in range(1, max_attempts + 1):
            started = time.perf_counter()
            payload = {
                "model": self._settings.ollama_model,
                "stream": False,
                "think": False,
                "format": schema,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                "options": {
                    "temperature": self._settings.ollama_temperature,
                    "top_p": self._settings.ollama_top_p,
                    "num_ctx": self._settings.ollama_num_ctx,
                },
                "keep_alive": self._settings.ollama_keep_alive,
            }

            try:
                response = self._client.post("/api/chat", json=payload)
            except httpx.TimeoutException as exc:
                logger.warning(
                    "Ollama timeout | model=%s | attempt=%s",
                    self._settings.ollama_model,
                    attempt,
                )
                raise OllamaTimeoutError("Délai Ollama dépassé") from exc
            except httpx.RequestError as exc:
                logger.warning(
                    "Ollama indisponible | model=%s | attempt=%s | error=%s",
                    self._settings.ollama_model,
                    attempt,
                    exc.__class__.__name__,
                )
                raise OllamaUnavailableError("Ollama indisponible") from exc

            duration_ms = int((time.perf_counter() - started) * 1000)

            if response.status_code == 404:
                logger.warning(
                    "Ollama modèle absent | model=%s | status=404",
                    self._settings.ollama_model,
                )
                raise OllamaModelUnavailableError("Modèle Ollama absent")

            if response.status_code >= 400:
                logger.warning(
                    "Ollama erreur HTTP | model=%s | status=%s | attempt=%s",
                    self._settings.ollama_model,
                    response.status_code,
                    attempt,
                )
                raise OllamaUnavailableError("Erreur HTTP Ollama")

            try:
                body = response.json()
            except ValueError:
                last_reason = "réponse HTTP non JSON"
                logger.warning(
                    "Ollama réponse HTTP illisible | model=%s | attempt=%s",
                    self._settings.ollama_model,
                    attempt,
                )
                continue

            message = body.get("message") if isinstance(body, dict) else None
            content = message.get("content") if isinstance(message, dict) else None
            if not isinstance(content, str) or not content.strip():
                last_reason = "réponse vide"
                logger.warning(
                    "Ollama réponse vide | model=%s | attempt=%s",
                    self._settings.ollama_model,
                    attempt,
                )
                continue

            raw_content = self._strip_code_fence(content.strip())
            try:
                output = output_model.model_validate_json(raw_content)
                if validate_output is not None:
                    validate_output(output)
            except (ValidationError, ValueError) as exc:
                last_reason = "JSON ou schéma invalide"
                logger.warning(
                    "Ollama sortie invalide | model=%s | attempt=%s | error=%s",
                    self._settings.ollama_model,
                    attempt,
                    exc.__class__.__name__,
                )
                continue

            prompt_tokens = body.get("prompt_eval_count")
            completion_tokens = body.get("eval_count")
            logger.info(
                "Ollama génération terminée | model=%s | attempt=%s | duration_ms=%s "
                "| prompt_tokens=%s | completion_tokens=%s",
                self._settings.ollama_model,
                attempt,
                duration_ms,
                prompt_tokens,
                completion_tokens,
            )
            return OllamaGenerationResult(
                output=output,
                prompt_token_count=(
                    int(prompt_tokens) if isinstance(prompt_tokens, int) else None
                ),
                completion_token_count=(
                    int(completion_tokens) if isinstance(completion_tokens, int) else None
                ),
                duration_ms=duration_ms,
                attempts=attempt,
            )

        logger.warning(
            "Ollama sortie rejetée après %s tentative(s) | model=%s | reason=%s",
            max_attempts,
            self._settings.ollama_model,
            last_reason,
        )
        raise OllamaOutputError("Sortie Ollama invalide")

    @staticmethod
    def _strip_code_fence(content: str) -> str:
        if not content.startswith("```"):
            return content
        lines = content.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        return "\n".join(lines).strip()


@lru_cache
def get_ollama_client() -> OllamaClient:
    return OllamaClient(get_settings())
