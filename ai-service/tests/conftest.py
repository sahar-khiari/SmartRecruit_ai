import math
import re
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.core.config import Settings, get_settings
from app.main import create_app
from app.models.chat import RagGenerationOutput
from app.services.anonymization_service import AnonymizationService
from app.services.chat_service import ChatService, get_chat_service
from app.services.chunking_service import ChunkingService
from app.services.embedding_service import EmbeddingService, get_embedding_service
from app.services.indexing_service import IndexingService, get_indexing_service
from app.services.ollama_client import (
    OllamaGenerationResult,
    OllamaHealth,
    get_ollama_client,
)
from app.services.retrieval_service import RetrievalService, get_retrieval_service
from app.services.text_cleaning_service import TextCleaningService
from app.services.vector_store_service import VectorStoreService, get_vector_store_service

TEST_API_KEY = "test-internal-key"


class FakeEmbeddingService:
    """Embedding déterministe pour tester le pipeline sans télécharger de modèle."""

    model_name = "fake-multilingual-local-model"

    _KEYWORDS = ("recrutement", "entretien", "candidat", "document")

    def ensure_loaded(self) -> bool:
        return True

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(text) for text in texts]

    def embed_query(self, text: str) -> list[float]:
        return self._embed(text)

    def _embed(self, text: str) -> list[float]:
        lowered = text.lower()
        vector = [float(lowered.count(keyword)) for keyword in self._KEYWORDS]
        if not any(vector):
            vector = [0.25, 0.25, 0.25, 0.25]
        norm = math.sqrt(sum(value * value for value in vector))
        return [round(value / norm, 8) for value in vector]


class FakeOllamaClient:
    """Client Ollama scriptable : la suite rapide ne dépend jamais du réseau."""

    def __init__(self) -> None:
        self.health_result = OllamaHealth(available=True, model_available=True)
        self.calls: list[dict[str, object]] = []
        self.next_output = RagGenerationOutput(
            answer="Réponse fondée.",
            category="OTHER",
            grounded=False,
            needsHumanReview=True,
            usedChunkIds=[],
        )
        self.error: Exception | None = None
        self.duration_ms = 12
        self.prompt_token_count = 10
        self.completion_token_count = 5

    def health(self) -> OllamaHealth:
        return self.health_result

    def generate_structured(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        output_model,
        validate_output=None,
    ) -> OllamaGenerationResult:
        self.calls.append(
            {
                "system_prompt": system_prompt,
                "user_prompt": user_prompt,
                "output_model": output_model,
            }
        )
        if self.error is not None:
            raise self.error
        output = output_model.model_validate(
            self.next_output.model_dump(by_alias=False)
        )
        if validate_output is not None:
            validate_output(output)
        return OllamaGenerationResult(
            output=output,
            prompt_token_count=self.prompt_token_count,
            completion_token_count=self.completion_token_count,
            duration_ms=self.duration_ms,
            attempts=1,
        )


@pytest.fixture
def test_settings(tmp_path: Path) -> Settings:
    return Settings(
        _env_file=None,
        ai_service_api_key=SecretStr(TEST_API_KEY),
        chroma_persist_dir=tmp_path / f"chroma-{re.sub(r'[^a-zA-Z0-9]', '', str(tmp_path))}",
        chroma_collection_name="hr_knowledge_test",
        chunk_size=200,
        chunk_overlap=40,
        embedding_model_name="fake-multilingual-local-model",
        min_relevance_score=0.30,
        log_level="WARNING",
    )


@pytest.fixture
def cleaning_service() -> TextCleaningService:
    return TextCleaningService()


@pytest.fixture
def anonymization_service(cleaning_service: TextCleaningService) -> AnonymizationService:
    return AnonymizationService(cleaning_service)


@pytest.fixture
def chunking_service(cleaning_service: TextCleaningService) -> ChunkingService:
    return ChunkingService(
        chunk_size=200,
        chunk_overlap=40,
        cleaning_service=cleaning_service,
    )


@pytest.fixture
def embedding_service() -> FakeEmbeddingService:
    return FakeEmbeddingService()


@pytest.fixture
def vector_store(test_settings: Settings) -> VectorStoreService:
    store = VectorStoreService(test_settings)
    yield store
    try:
        store.reset_collection()
    except Exception:
        pass


@pytest.fixture
def indexing_service(
    test_settings: Settings,
    cleaning_service: TextCleaningService,
    anonymization_service: AnonymizationService,
    chunking_service: ChunkingService,
    embedding_service: FakeEmbeddingService,
    vector_store: VectorStoreService,
) -> IndexingService:
    return IndexingService(
        settings=test_settings,
        cleaning_service=cleaning_service,
        anonymization_service=anonymization_service,
        chunking_service=chunking_service,
        embedding_service=embedding_service,
        vector_store=vector_store,
    )


@pytest.fixture
def retrieval_service(
    test_settings: Settings,
    cleaning_service: TextCleaningService,
    anonymization_service: AnonymizationService,
    embedding_service: FakeEmbeddingService,
    vector_store: VectorStoreService,
) -> RetrievalService:
    return RetrievalService(
        settings=test_settings,
        cleaning_service=cleaning_service,
        anonymization_service=anonymization_service,
        embedding_service=embedding_service,
        vector_store=vector_store,
    )


@pytest.fixture
def fake_ollama() -> FakeOllamaClient:
    return FakeOllamaClient()


@pytest.fixture
def chat_service(
    test_settings: Settings,
    retrieval_service: RetrievalService,
    fake_ollama: FakeOllamaClient,
) -> ChatService:
    return ChatService(
        settings=test_settings,
        retrieval_service=retrieval_service,
        ollama_client=fake_ollama,
    )


@pytest.fixture
def client(
    test_settings: Settings,
    embedding_service: FakeEmbeddingService,
    vector_store: VectorStoreService,
    indexing_service: IndexingService,
    retrieval_service: RetrievalService,
    chat_service: ChatService,
    fake_ollama: FakeOllamaClient,
) -> TestClient:
    application = create_app()
    application.dependency_overrides[get_settings] = lambda: test_settings
    application.dependency_overrides[get_embedding_service] = lambda: embedding_service
    application.dependency_overrides[get_vector_store_service] = lambda: vector_store
    application.dependency_overrides[get_indexing_service] = lambda: indexing_service
    application.dependency_overrides[get_retrieval_service] = lambda: retrieval_service
    application.dependency_overrides[get_chat_service] = lambda: chat_service
    application.dependency_overrides[get_ollama_client] = lambda: fake_ollama
    with TestClient(application) as test_client:
        yield test_client
    application.dependency_overrides.clear()


@pytest.fixture
def auth_headers() -> dict[str, str]:
    return {"X-AI-Service-Key": TEST_API_KEY}
