import time
from functools import lru_cache

from app.core.config import Settings, get_settings
from app.models.retrieval import (
    RetrievalSearchRequest,
    RetrievalSearchResponse,
    RetrievedChunk,
)
from app.services.anonymization_service import AnonymizationService
from app.services.embedding_service import EmbeddingService, get_embedding_service
from app.services.text_cleaning_service import TextCleaningService
from app.services.vector_store_service import VectorStoreService, get_vector_store_service


class RetrievalService:
    """Recherche sémantique pure : retourne des chunks, jamais une réponse générée."""

    def __init__(
        self,
        *,
        settings: Settings,
        cleaning_service: TextCleaningService,
        anonymization_service: AnonymizationService,
        embedding_service: EmbeddingService,
        vector_store: VectorStoreService,
    ) -> None:
        self._settings = settings
        self._cleaning = cleaning_service
        self._anonymization = anonymization_service
        self._embedding = embedding_service
        self._vector_store = vector_store

    def search(self, request: RetrievalSearchRequest) -> RetrievalSearchResponse:
        started = time.perf_counter()

        cleaned_query = self._cleaning.clean(request.query)
        sanitized_query = self._anonymization.anonymize(cleaned_query).text
        if not sanitized_query.strip():
            raise ValueError("La question est vide après anonymisation")

        top_k = min(request.top_k, self._settings.max_top_k)
        embedding = self._embedding.embed_query(sanitized_query)
        hits = self._vector_store.search(
            query_embedding=embedding,
            top_k=top_k,
            categories=request.categories,
            document_ids=request.normalized_document_ids(),
            min_score=self._settings.min_relevance_score,
        )

        results = [
            RetrievedChunk(
                documentId=hit.document_id,
                chunkId=hit.chunk_id,
                chunkIndex=hit.chunk_index,
                title=hit.title,
                category=hit.category,
                content=hit.content,
                score=hit.score,
                version=hit.version,
            )
            for hit in hits
        ]
        duration_ms = int((time.perf_counter() - started) * 1000)

        return RetrievalSearchResponse(
            query=request.query,
            sanitizedQuery=sanitized_query,
            results=results,
            totalResults=len(results),
            modelName=self._embedding.model_name,
            durationMs=duration_ms,
        )


@lru_cache
def get_retrieval_service() -> RetrievalService:
    settings = get_settings()
    return RetrievalService(
        settings=settings,
        cleaning_service=TextCleaningService(),
        anonymization_service=AnonymizationService(),
        embedding_service=get_embedding_service(),
        vector_store=get_vector_store_service(),
    )
