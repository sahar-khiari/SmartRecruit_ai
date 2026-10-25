"""Client Gemini (API REST generateContent) avec sortie JSON structurée native.

Appel HTTP direct avec httpx : pas de SDK supplémentaire à installer.
La clé passe dans l'en-tête x-goog-api-key (jamais dans l'URL, donc jamais dans les logs).
"""

import logging
import time
from typing import Any

import httpx

from app.coreAgents.Agents.cv_analysis.llm.base import (
    LlmAuthError,
    LlmNotConfiguredError,
    LlmOutputError,
    LlmQuotaError,
    LlmRequestError,
    LlmResult,
    LlmTimeoutError,
    LlmUnavailableError,
)

logger = logging.getLogger(__name__)


class GeminiClient:

    def __init__(self, api_key: str | None, model: str, base_url: str,
                 timeout_seconds: float, temperature: float,
                 http_client: httpx.Client | None = None) -> None:
        self.model = model
        self._api_key = api_key
        self._url = f"{base_url.rstrip('/')}/models/{model}:generateContent"
        self._timeout = timeout_seconds
        self._temperature = temperature
        self._http = http_client or httpx.Client()

    def generate_json(self, system: str, prompt: str, schema: dict[str, Any]) -> LlmResult:
        if not self._api_key:
            raise LlmNotConfiguredError("GEMINI_API_KEY n'est pas configurée", self.model)

        body = {
            "systemInstruction": {"parts": [{"text": system}]},
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": self._temperature,
                "responseMimeType": "application/json",
                "responseSchema": schema,
            },
        }
        started = time.perf_counter()
        try:
            response = self._http.post(self._url, json=body, timeout=self._timeout,
                                       headers={"x-goog-api-key": self._api_key})
        except httpx.TimeoutException as exc:
            raise LlmTimeoutError(f"Gemini n'a pas répondu en {self._timeout:g} s", self.model) from exc
        except httpx.HTTPError as exc:
            raise LlmUnavailableError(f"Gemini injoignable ({type(exc).__name__})", self.model) from exc
        latency_ms = round((time.perf_counter() - started) * 1000)

        self._raise_for_status(response.status_code)
        try:
            payload = response.json()
            candidate = payload["candidates"][0]
            text = "".join(part.get("text", "") for part in candidate["content"]["parts"])
        except (ValueError, KeyError, IndexError, TypeError) as exc:
            reason = (payload.get("promptFeedback", {}).get("blockReason")
                      if isinstance(payload, dict) else None)
            raise LlmOutputError(f"Réponse Gemini sans contenu{f' ({reason})' if reason else ''}",
                                 self.model) from exc
        if not text.strip():
            raise LlmOutputError("Réponse Gemini vide", self.model)

        usage = payload.get("usageMetadata", {})
        return LlmResult(
            text=text,
            model=self.model,
            latency_ms=latency_ms,
            input_tokens=usage.get("promptTokenCount"),
            output_tokens=usage.get("candidatesTokenCount"),
        )

    def _raise_for_status(self, status: int) -> None:
        if status == 200:
            return
        # Le corps de l'erreur n'est pas journalisé : il peut reprendre une partie de la requête.
        logger.warning("Gemini en erreur | model=%s status=%d", self.model, status)
        if status in (401, 403):
            raise LlmAuthError("Clé Gemini refusée", self.model)
        if status == 429:
            raise LlmQuotaError("Quota Gemini dépassé", self.model)
        if status in (400, 404):
            raise LlmRequestError(f"Requête refusée par Gemini (HTTP {status})", self.model)
        raise LlmUnavailableError(f"Gemini indisponible (HTTP {status})", self.model)
