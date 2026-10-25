import logging
import threading
from dataclasses import dataclass
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from typing import Any

from app.core.config import Settings, get_settings
from app.models.knowledge import KnowledgeCategory
from app.services.chunking_service import DocumentChunk

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class VectorSearchHit:
    document_id: int
    chunk_id: str
    chunk_index: int
    title: str
    category: KnowledgeCategory
    content: str
    score: float
    version: int


class VectorStoreService:
    """Stockage vectoriel local ChromaDB.

    ChromaDB n'est jamais la source de vérité : MariaDB conserve les documents.
    L'index peut donc être supprimé et reconstruit sans perte métier.
    """

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client = None
        self._collection = None
        self._lock = threading.Lock()

    @property
    def collection_name(self) -> str:
        return self._settings.chroma_collection_name

    def _get_collection(self):
        if self._collection is not None:
            return self._collection

        with self._lock:
            if self._collection is None:
                import chromadb
                from chromadb.config import Settings as ChromaSettings

                persist_dir = Path(self._settings.chroma_persist_dir)
                persist_dir.mkdir(parents=True, exist_ok=True)
                self._client = chromadb.PersistentClient(
                    path=str(persist_dir),
                    settings=ChromaSettings(anonymized_telemetry=False),
                )
                self._collection = self._client.get_or_create_collection(
                    name=self._settings.chroma_collection_name,
                    metadata={"hnsw:space": "cosine"},
                )
        return self._collection

    def upsert_chunks(
        self,
        chunks: list[DocumentChunk],
        embeddings: list[list[float]],
        *,
        indexed_at: datetime,
    ) -> None:
        if not chunks:
            return
        if len(chunks) != len(embeddings):
            raise ValueError("Le nombre de chunks et d'embeddings doit être identique")

        collection = self._get_collection()
        indexed_at_value = indexed_at.isoformat()
        collection.upsert(
            ids=[chunk.chunk_id for chunk in chunks],
            embeddings=embeddings,
            documents=[chunk.content for chunk in chunks],
            metadatas=[
                {
                    "documentId": chunk.document_id,
                    "chunkIndex": chunk.chunk_index,
                    "title": chunk.title,
                    "category": chunk.category.value,
                    "version": chunk.version,
                    "active": True,
                    "indexedAt": indexed_at_value,
                }
                for chunk in chunks
            ],
        )

    def delete_document(self, document_id: int) -> int:
        collection = self._get_collection()
        existing = collection.get(
            where={"documentId": int(document_id)},
            include=[],
        )
        ids = existing.get("ids") or []
        if ids:
            collection.delete(ids=ids)
        return len(ids)

    def count_document_chunks(self, document_id: int) -> int:
        collection = self._get_collection()
        existing = collection.get(
            where={"documentId": int(document_id)},
            include=[],
        )
        return len(existing.get("ids") or [])

    def count_all_chunks(self) -> int:
        return int(self._get_collection().count())

    def collection_health(self) -> bool:
        try:
            self._get_collection().count()
            return True
        except Exception as exc:  # noqa: BLE001 - état exposé sans détail sensible
            logger.warning("ChromaDB indisponible (%s)", exc.__class__.__name__)
            return False

    def search(
        self,
        *,
        query_embedding: list[float],
        top_k: int,
        categories: list[KnowledgeCategory] | None = None,
        document_ids: list[int] | None = None,
        min_score: float = 0.0,
    ) -> list[VectorSearchHit]:
        collection = self._get_collection()
        total = int(collection.count())
        if total == 0:
            return []

        n_results = min(total, max(top_k * 3, top_k))
        result: dict[str, Any] = collection.query(
            query_embeddings=[query_embedding],
            n_results=n_results,
            where=self._build_where(categories, document_ids),
            include=["documents", "metadatas", "distances"],
        )

        ids = (result.get("ids") or [[]])[0]
        documents = (result.get("documents") or [[]])[0]
        metadatas = (result.get("metadatas") or [[]])[0]
        distances = (result.get("distances") or [[]])[0]

        hits: list[VectorSearchHit] = []
        for chunk_id, document, metadata, distance in zip(
            ids, documents, metadatas, distances, strict=False
        ):
            if document is None or metadata is None:
                continue
            # Chroma cosine retourne une distance. Le score n'est pas une probabilité.
            score = round(max(0.0, min(1.0, 1.0 - float(distance))), 6)
            if score < min_score:
                continue
            hits.append(
                VectorSearchHit(
                    document_id=int(metadata["documentId"]),
                    chunk_id=str(chunk_id),
                    chunk_index=int(metadata["chunkIndex"]),
                    title=str(metadata["title"]),
                    category=KnowledgeCategory(str(metadata["category"])),
                    content=str(document),
                    score=score,
                    version=int(metadata["version"]),
                )
            )

        hits.sort(key=lambda hit: (-hit.score, hit.document_id, hit.chunk_index))
        return hits[:top_k]

    @staticmethod
    def _build_where(
        categories: list[KnowledgeCategory] | None,
        document_ids: list[int] | None,
    ) -> dict[str, Any] | None:
        clauses: list[dict[str, Any]] = []
        if categories:
            clauses.append({"category": {"$in": [category.value for category in categories]}})
        if document_ids:
            clauses.append({"documentId": {"$in": [int(value) for value in document_ids]}})
        if not clauses:
            return None
        if len(clauses) == 1:
            return clauses[0]
        return {"$and": clauses}

    def reset_collection(self) -> None:
        """Réservé aux tests et aux opérations de maintenance locales.

        Cette méthode n'est exposée par aucun endpoint HTTP.
        """
        collection = self._get_collection()
        self._get_client().delete_collection(collection.name)
        self._collection = None

    def _get_client(self):
        if self._client is None:
            self._get_collection()
        return self._client


@lru_cache
def get_vector_store_service() -> VectorStoreService:
    return VectorStoreService(get_settings())
