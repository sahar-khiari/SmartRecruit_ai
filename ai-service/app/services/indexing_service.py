import logging
from datetime import datetime, timezone
from functools import lru_cache

from app.core.config import Settings, get_settings
from app.models.knowledge import (
    IndexingStatus,
    KnowledgeDocumentIndexRequest,
    KnowledgeDocumentIndexResponse,
)
from app.services.anonymization_service import AnonymizationService
from app.services.chunking_service import ChunkingService
from app.services.embedding_service import EmbeddingService, get_embedding_service
from app.services.text_cleaning_service import TextCleaningService
from app.services.vector_store_service import VectorStoreService, get_vector_store_service

logger = logging.getLogger(__name__)


class IndexingService:
    """Indexation idempotente d'un document RH actif.

    Les embeddings sont calculés avant de supprimer les anciens chunks : un
    échec du modèle ne détruit donc pas l'index existant avant l'étape finale.
    """

    def __init__(
        self,
        *,
        settings: Settings,
        cleaning_service: TextCleaningService,
        anonymization_service: AnonymizationService,
        chunking_service: ChunkingService,
        embedding_service: EmbeddingService,
        vector_store: VectorStoreService,
    ) -> None:
        self._settings = settings
        self._cleaning = cleaning_service
        self._anonymization = anonymization_service
        self._chunking = chunking_service
        self._embedding = embedding_service
        self._vector_store = vector_store

    def index(self, request: KnowledgeDocumentIndexRequest) -> KnowledgeDocumentIndexResponse:
        now = datetime.now(timezone.utc)

        if not request.active:
            deleted = self._vector_store.delete_document(request.document_id)
            logger.info(
                "Document %s inactif : %s chunk(s) retiré(s) de l'index.",
                request.document_id,
                deleted,
            )
            return KnowledgeDocumentIndexResponse.build(
                document_id=request.document_id,
                version=request.version,
                status=IndexingStatus.NOT_INDEXED,
                chunks_indexed=0,
                anonymized_items_count=0,
                model_name=self._embedding.model_name,
                indexed_at=now,
            )

        cleaned_title = self._cleaning.clean(request.title)
        cleaned_content = self._cleaning.clean(request.content)

        title_result = self._anonymization.anonymize(cleaned_title)
        content_result = self._anonymization.anonymize(cleaned_content)

        chunks = self._chunking.chunk_document(
            document_id=request.document_id,
            version=request.version,
            title=title_result.text,
            content=content_result.text,
            category=request.category,
        )
        if not chunks:
            raise ValueError("Le document ne contient aucun passage indexable")

        embeddings = self._embedding.embed_documents([chunk.content for chunk in chunks])
        if len(embeddings) != len(chunks):
            raise ValueError("Le modèle d'embeddings a retourné un nombre de vecteurs incohérent")

        # Remplacement atomique du point de vue logique : aucun ancien chunk ne survit.
        self._vector_store.delete_document(request.document_id)
        self._vector_store.upsert_chunks(chunks, embeddings, indexed_at=now)

        logger.info(
            "Document %s v%s indexé en %s chunk(s).",
            request.document_id,
            request.version,
            len(chunks),
        )
        return KnowledgeDocumentIndexResponse.build(
            document_id=request.document_id,
            version=request.version,
            status=IndexingStatus.INDEXED,
            chunks_indexed=len(chunks),
            anonymized_items_count=(
                title_result.replaced_count + content_result.replaced_count
            ),
            model_name=self._embedding.model_name,
            indexed_at=now,
        )


@lru_cache
def get_indexing_service() -> IndexingService:
    settings = get_settings()
    return IndexingService(
        settings=settings,
        cleaning_service=TextCleaningService(),
        anonymization_service=AnonymizationService(),
        chunking_service=ChunkingService(
            chunk_size=settings.chunk_size,
            chunk_overlap=settings.chunk_overlap,
        ),
        embedding_service=get_embedding_service(),
        vector_store=get_vector_store_service(),
    )
