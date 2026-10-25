"""Client HTTP Cohere (API v2) pour le Module 2.

Gère les cas d'erreur exigés : clé absente, délai dépassé, quota atteint,
réponse invalide. Utilise httpx (déjà présent) : aucune dépendance ajoutée.
"""
from __future__ import annotations

import json
import logging
import time
from typing import Any

import httpx

from app.offres.config import OffresSettings

logger = logging.getLogger(__name__)


class CohereError(Exception):
    """Erreur générique Cohere, avec un code stable et un statut HTTP à renvoyer."""

    status_code = 502
    error_code = "COHERE_ERROR"

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message


class CohereKeyMissingError(CohereError):
    status_code = 503
    error_code = "COHERE_KEY_MISSING"


class CohereTimeoutError(CohereError):
    status_code = 504
    error_code = "COHERE_TIMEOUT"


class CohereQuotaError(CohereError):
    status_code = 429
    error_code = "COHERE_QUOTA"


class CohereUnavailableError(CohereError):
    status_code = 503
    error_code = "COHERE_UNAVAILABLE"


class CohereInvalidOutputError(CohereError):
    status_code = 502
    error_code = "COHERE_INVALID_OUTPUT"


class CohereClient:
    def __init__(self, settings: OffresSettings, http_client: httpx.Client | None = None) -> None:
        self._settings = settings
        self._http = http_client or httpx.Client(timeout=settings.cohere_timeout_seconds)

    # ------------------------------------------------------------------ public

    def chat_json(self, system_prompt: str, user_prompt: str, temperature: float = 0.2) -> dict[str, Any]:
        """Envoie un prompt et renvoie la réponse JSON décodée (un objet)."""
        payload = {
            "model": self._settings.cohere_chat_model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": temperature,
            "response_format": {"type": "json_object"},
        }
        data = self._post("/v2/chat", payload)
        try:
            text = data["message"]["content"][0]["text"]
            parsed = json.loads(text)
        except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
            raise CohereInvalidOutputError("La réponse de Cohere n'est pas un JSON valide") from exc
        if not isinstance(parsed, dict):
            raise CohereInvalidOutputError("La réponse de Cohere doit être un objet JSON")
        return parsed

    def embed(self, texts: list[str], input_type: str) -> list[list[float]]:
        """Calcule les embeddings (par lots de 96 textes, limite de l'API)."""
        vectors: list[list[float]] = []
        for start in range(0, len(texts), 96):
            batch = texts[start:start + 96]
            data = self._post("/v2/embed", {
                "model": self._settings.cohere_embed_model,
                "texts": batch,
                "input_type": input_type,
                "embedding_types": ["float"],
            })
            try:
                vectors.extend(data["embeddings"]["float"])
            except (KeyError, TypeError) as exc:
                raise CohereInvalidOutputError("Réponse d'embedding Cohere invalide") from exc
        return vectors

    # ----------------------------------------------------------------- private

    def _post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        key = self._settings.cohere_key_value
        if key is None:
            raise CohereKeyMissingError(
                "Clé Cohere absente : ajoutez COHERE_API_KEY dans ai-service/.env"
            )

        url = self._settings.cohere_base_url.rstrip("/") + path
        headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
        attempts = 1 + self._settings.cohere_max_retries
        last_error: CohereError | None = None

        for attempt in range(1, attempts + 1):
            started = time.perf_counter()
            try:
                response = self._http.post(url, json=payload, headers=headers)
            except httpx.TimeoutException:
                last_error = CohereTimeoutError("Cohere n'a pas répondu dans le délai autorisé")
            except httpx.HTTPError:
                last_error = CohereUnavailableError("Cohere est injoignable")
            else:
                elapsed_ms = int((time.perf_counter() - started) * 1000)
                logger.info("Cohere %s | status=%s | %d ms | essai %d", path, response.status_code,
                            elapsed_ms, attempt)
                if response.status_code == 200:
                    return response.json()
                if response.status_code in (401, 403):
                    # Inutile de réessayer : la clé est refusée.
                    raise CohereKeyMissingError("Clé Cohere invalide ou refusée")
                if response.status_code == 429:
                    raise CohereQuotaError("Quota Cohere atteint, réessayez plus tard")
                if response.status_code >= 500:
                    last_error = CohereUnavailableError("Cohere est temporairement indisponible")
                else:
                    raise CohereError(f"Requête refusée par Cohere (HTTP {response.status_code})")

            if attempt < attempts:
                time.sleep(0.5 * attempt)

        assert last_error is not None
        raise last_error
