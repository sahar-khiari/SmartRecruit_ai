from fastapi.testclient import TestClient

from app.services.ollama_client import OllamaHealth


def test_health_is_public_and_reports_up(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["service"] == "smartrecruit-ai-service"
    assert body["version"] == "1.1.0"
    assert body["embeddingModel"] == "fake-multilingual-local-model"
    assert body["chromaAvailable"] is True
    assert body["ollamaAvailable"] is True
    assert body["llmModel"] == "qwen3:4b"
    assert body["llmModelAvailable"] is True
    assert body["timestamp"]


def test_health_is_degraded_when_ollama_is_unavailable(
    client: TestClient, fake_ollama
) -> None:
    fake_ollama.health_result = OllamaHealth(available=False, model_available=False)

    body = client.get("/health").json()

    assert body["status"] == "DEGRADED"
    assert body["ollamaAvailable"] is False
    assert body["llmModelAvailable"] is False


def test_health_does_not_expose_secrets_or_paths(
    client: TestClient, test_settings
) -> None:
    response = client.get("/health")
    raw = response.text.lower()

    assert "test-internal-key" not in raw
    assert "api_key" not in raw
    assert "apikey" not in raw
    assert "chroma_persist" not in raw
    assert str(test_settings.chroma_persist_dir).lower() not in raw
    assert "stack" not in raw
