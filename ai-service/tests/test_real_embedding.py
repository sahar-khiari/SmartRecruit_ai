import os

import pytest

from app.core.config import Settings
from app.services.embedding_service import EmbeddingService


@pytest.mark.real_embedding
@pytest.mark.skipif(
    os.getenv("RUN_REAL_EMBEDDING_TEST") != "1",
    reason="Test réel désactivé par défaut (RUN_REAL_EMBEDDING_TEST=1 pour l'activer)",
)
def test_real_local_embedding_model_generates_vectors(tmp_path) -> None:
    settings = Settings(
        _env_file=None,
        chroma_persist_dir=tmp_path / "chroma",
        embedding_device="cpu",
        log_level="WARNING",
    )
    service = EmbeddingService(settings)

    vectors = service.embed_documents(
        [
            "Le processus de recrutement comporte un entretien.",
            "La préparation à l'entretien est importante.",
        ]
    )

    assert len(vectors) == 2
    assert len(vectors[0]) > 100
    assert len(vectors[0]) == len(vectors[1])
