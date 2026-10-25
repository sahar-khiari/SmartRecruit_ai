import json

import httpx
import pytest

from app.models.chat import RagGenerationOutput
from app.services.ollama_client import (
    OllamaClient,
    OllamaModelUnavailableError,
    OllamaOutputError,
    OllamaTimeoutError,
    OllamaUnavailableError,
)


def output_payload(**overrides) -> dict:
    payload = {
        "answer": "Consultez les offres depuis votre espace candidat.",
        "category": "PLATFORM_HELP",
        "grounded": True,
        "needsHumanReview": False,
        "usedChunkIds": ["document-1-v1-chunk-0"],
    }
    payload.update(overrides)
    return payload


def chat_response(content: str, *, prompt_tokens: int = 10, completion_tokens: int = 5) -> httpx.Response:
    return httpx.Response(
        200,
        json={
            "model": "qwen3:4b",
            "message": {"role": "assistant", "content": content},
            "prompt_eval_count": prompt_tokens,
            "eval_count": completion_tokens,
        },
    )


def make_client(test_settings, handler) -> OllamaClient:
    return OllamaClient(test_settings, transport=httpx.MockTransport(handler))


def test_health_reports_server_and_model_available(test_settings) -> None:
    client = make_client(
        test_settings,
        lambda request: httpx.Response(
            200, json={"models": [{"name": "qwen3:4b"}, {"name": "other:1b"}]}
        ),
    )

    health = client.health()

    assert health.available is True
    assert health.model_available is True


def test_health_reports_missing_model(test_settings) -> None:
    client = make_client(
        test_settings,
        lambda request: httpx.Response(200, json={"models": [{"name": "other:1b"}]}),
    )

    health = client.health()

    assert health.available is True
    assert health.model_available is False


def test_health_reports_ollama_unavailable(test_settings) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    health = make_client(test_settings, handler).health()

    assert health.available is False
    assert health.model_available is False


def test_generate_structured_sends_schema_and_options(test_settings) -> None:
    captured: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured.update(json.loads(request.content))
        return chat_response(json.dumps(output_payload()))

    result = make_client(test_settings, handler).generate_structured(
        system_prompt="system",
        user_prompt="user",
        output_model=RagGenerationOutput,
    )

    assert result.output.answer.startswith("Consultez")
    assert result.prompt_token_count == 10
    assert result.completion_token_count == 5
    assert result.attempts == 1
    assert captured["model"] == "qwen3:4b"
    assert captured["stream"] is False
    assert captured["format"]["type"] == "object"
    assert captured["format"]["additionalProperties"] is False
    assert captured["options"]["temperature"] == 0.1
    assert captured["options"]["top_p"] == 0.9
    assert captured["options"]["num_ctx"] == 8192
    assert captured["keep_alive"] == "10m"


def test_generate_structured_retries_once_after_empty_response(test_settings) -> None:
    calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        if calls == 1:
            return chat_response("")
        return chat_response(json.dumps(output_payload()))

    result = make_client(test_settings, handler).generate_structured(
        system_prompt="system",
        user_prompt="user",
        output_model=RagGenerationOutput,
    )

    assert calls == 2
    assert result.attempts == 2


def test_generate_structured_rejects_invalid_json_after_one_retry(test_settings) -> None:
    calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return chat_response("pas du json")

    with pytest.raises(OllamaOutputError):
        make_client(test_settings, handler).generate_structured(
            system_prompt="system",
            user_prompt="user",
            output_model=RagGenerationOutput,
        )

    assert calls == 2


def test_generate_structured_rejects_schema_violation(test_settings) -> None:
    calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return chat_response(json.dumps(output_payload(unexpected="field")))

    with pytest.raises(OllamaOutputError):
        make_client(test_settings, handler).generate_structured(
            system_prompt="system",
            user_prompt="user",
            output_model=RagGenerationOutput,
        )

    assert calls == 2


def test_generate_structured_uses_validator_retry(test_settings) -> None:
    calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return chat_response(json.dumps(output_payload()))

    def validate(output: RagGenerationOutput) -> None:
        if output.answer.startswith("Consultez"):
            raise ValueError("refusé")

    with pytest.raises(OllamaOutputError):
        make_client(test_settings, handler).generate_structured(
            system_prompt="system",
            user_prompt="user",
            output_model=RagGenerationOutput,
            validate_output=validate,
        )

    assert calls == 2


def test_generate_structured_raises_timeout(test_settings) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("timeout", request=request)

    with pytest.raises(OllamaTimeoutError):
        make_client(test_settings, handler).generate_structured(
            system_prompt="system",
            user_prompt="user",
            output_model=RagGenerationOutput,
        )


def test_generate_structured_raises_unavailable_on_connection_error(test_settings) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    with pytest.raises(OllamaUnavailableError):
        make_client(test_settings, handler).generate_structured(
            system_prompt="system",
            user_prompt="user",
            output_model=RagGenerationOutput,
        )


def test_generate_structured_reports_missing_model(test_settings) -> None:
    client = make_client(
        test_settings,
        lambda request: httpx.Response(404, json={"error": "model not found"}),
    )

    with pytest.raises(OllamaModelUnavailableError):
        client.generate_structured(
            system_prompt="system",
            user_prompt="user",
            output_model=RagGenerationOutput,
        )


def test_generate_structured_accepts_fenced_json(test_settings) -> None:
    client = make_client(
        test_settings,
        lambda request: chat_response(
            "```json\n" + json.dumps(output_payload()) + "\n```"
        ),
    )

    result = client.generate_structured(
        system_prompt="system",
        user_prompt="user",
        output_model=RagGenerationOutput,
    )

    assert result.output.grounded is True
