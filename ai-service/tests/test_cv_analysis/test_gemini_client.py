"""Client Gemini testé avec un transport HTTP simulé (aucun appel réseau)."""

import json

import httpx
import pytest

from app.coreAgents.Agents.cv_analysis.llm.base import (
    LlmAuthError,
    LlmNotConfiguredError,
    LlmOutputError,
    LlmQuotaError,
    LlmRequestError,
    LlmTimeoutError,
    LlmUnavailableError,
)
from app.coreAgents.Agents.cv_analysis.llm.gemini_client import GeminiClient

KEY = "cle-de-test"


def make_client(handler, api_key=KEY) -> GeminiClient:
    return GeminiClient(api_key, "gemini-test", "https://gemini.test/v1beta", 10, 0.1,
                        http_client=httpx.Client(transport=httpx.MockTransport(handler)))


def ok_response(text: str) -> httpx.Response:
    return httpx.Response(200, json={
        "candidates": [{"content": {"parts": [{"text": text}]}}],
        "usageMetadata": {"promptTokenCount": 321, "candidatesTokenCount": 45},
    })


def test_sends_key_in_header_and_returns_text_and_tokens():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["key"] = request.headers.get("x-goog-api-key")
        seen["body"] = json.loads(request.content)
        return ok_response('{"skills": ["Java"]}')

    result = make_client(handler).generate_json("système", "prompt", {"type": "OBJECT"})

    assert seen["url"] == "https://gemini.test/v1beta/models/gemini-test:generateContent"
    assert KEY not in seen["url"], "la clé ne doit jamais apparaître dans l'URL"
    assert seen["key"] == KEY
    assert seen["body"]["generationConfig"]["responseMimeType"] == "application/json"
    assert result.text == '{"skills": ["Java"]}'
    assert (result.input_tokens, result.output_tokens) == (321, 45)


@pytest.mark.parametrize(
    ("status", "error"),
    [(401, LlmAuthError), (403, LlmAuthError), (429, LlmQuotaError), (400, LlmRequestError),
     (404, LlmRequestError), (500, LlmUnavailableError), (503, LlmUnavailableError)],
)
def test_maps_http_errors(status, error):
    with pytest.raises(error):
        make_client(lambda _: httpx.Response(status, json={"error": {}})).generate_json("s", "p", {})


def test_timeout_and_network_errors():
    def timeout(request):
        raise httpx.ReadTimeout("lent", request=request)

    def offline(request):
        raise httpx.ConnectError("hors ligne", request=request)

    with pytest.raises(LlmTimeoutError):
        make_client(timeout).generate_json("s", "p", {})
    with pytest.raises(LlmUnavailableError):
        make_client(offline).generate_json("s", "p", {})


def test_blocked_or_empty_answer_is_an_output_error():
    blocked = httpx.Response(200, json={"promptFeedback": {"blockReason": "SAFETY"}})
    with pytest.raises(LlmOutputError, match="SAFETY"):
        make_client(lambda _: blocked).generate_json("s", "p", {})
    with pytest.raises(LlmOutputError):
        make_client(lambda _: ok_response("  ")).generate_json("s", "p", {})


def test_missing_key_fails_before_any_call():
    calls = []
    with pytest.raises(LlmNotConfiguredError):
        make_client(lambda r: calls.append(r) or ok_response("{}"), api_key=None).generate_json("s", "p", {})
    assert calls == []
