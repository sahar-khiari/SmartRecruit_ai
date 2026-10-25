import os

import pytest

from app.core.config import Settings
from app.models.chat import RagGenerationOutput
from app.services.ollama_client import OllamaClient


@pytest.mark.real_ollama
@pytest.mark.skipif(
    os.getenv("RUN_REAL_OLLAMA_TEST") != "1",
    reason="Test réel désactivé par défaut (RUN_REAL_OLLAMA_TEST=1 pour l'activer)",
)
def test_real_qwen_model_returns_valid_structured_output() -> None:
    settings = Settings(_env_file=None, log_level="WARNING")
    client = OllamaClient(settings)
    try:
        health = client.health()
        assert health.available is True
        assert health.model_available is True

        result = client.generate_structured(
            system_prompt=(
                "Tu réponds uniquement en JSON strict. "
                "N'utilise aucune connaissance externe. /no_think"
            ),
            user_prompt=(
                "Réponds avec answer='OK', category='OTHER', grounded=false, "
                "needsHumanReview=true et usedChunkIds=[]."
            ),
            output_model=RagGenerationOutput,
        )
    finally:
        client.close()

    assert result.output.answer
    assert result.output.category.value == "OTHER"
    assert result.output.grounded is False
    assert result.output.used_chunk_ids == []
    assert result.duration_ms >= 0
    assert result.attempts >= 1
