from app.models.knowledge import (
    IndexingStatus,
    KnowledgeDocumentIndexRequest,
)
from app.services.indexing_service import IndexingService
from app.services.vector_store_service import VectorStoreService


def build_request(**overrides) -> KnowledgeDocumentIndexRequest:
    payload = {
        "documentId": 7,
        "title": "Processus de recrutement",
        "content": "Le processus de recrutement comporte un entretien technique.",
        "category": "RECRUITMENT_PROCEDURE",
        "version": 1,
        "active": True,
    }
    payload.update(overrides)
    return KnowledgeDocumentIndexRequest(**payload)


def stored_chunks(vector_store: VectorStoreService, document_id: int):
    return vector_store._get_collection().get(
        where={"documentId": document_id},
        include=["metadatas", "documents"],
    )


def test_active_document_is_indexed(
    indexing_service: IndexingService, vector_store: VectorStoreService
) -> None:
    response = indexing_service.index(build_request())

    assert response.status == IndexingStatus.INDEXED
    assert response.document_id == 7
    assert response.version == 1
    assert response.chunks_indexed > 0
    assert response.model_name == "fake-multilingual-local-model"
    assert response.indexed_at is not None
    assert vector_store.count_document_chunks(7) == response.chunks_indexed


def test_inactive_document_is_not_indexed(
    indexing_service: IndexingService, vector_store: VectorStoreService
) -> None:
    response = indexing_service.index(build_request(active=False))

    assert response.status == IndexingStatus.NOT_INDEXED
    assert response.chunks_indexed == 0
    assert vector_store.count_document_chunks(7) == 0


def test_deactivation_removes_existing_chunks(
    indexing_service: IndexingService, vector_store: VectorStoreService
) -> None:
    indexing_service.index(build_request())
    assert vector_store.count_document_chunks(7) > 0

    response = indexing_service.index(build_request(active=False, version=2))

    assert response.status == IndexingStatus.NOT_INDEXED
    assert vector_store.count_document_chunks(7) == 0


def test_reindexing_same_version_is_idempotent(
    indexing_service: IndexingService, vector_store: VectorStoreService
) -> None:
    request = build_request()
    first = indexing_service.index(request)
    first_ids = stored_chunks(vector_store, 7)["ids"]
    second = indexing_service.index(request)
    second_ids = stored_chunks(vector_store, 7)["ids"]

    assert first.chunks_indexed == second.chunks_indexed
    assert sorted(first_ids) == sorted(second_ids)
    assert vector_store.count_document_chunks(7) == first.chunks_indexed


def test_new_version_replaces_all_old_chunks(
    indexing_service: IndexingService, vector_store: VectorStoreService
) -> None:
    indexing_service.index(build_request(version=1))
    assert any("v1" in chunk_id for chunk_id in stored_chunks(vector_store, 7)["ids"])

    indexing_service.index(
        build_request(
            version=2,
            content="Le recrutement contient aussi un entretien final et une décision.",
        )
    )

    ids = stored_chunks(vector_store, 7)["ids"]
    assert ids
    assert all("-v2-chunk-" in chunk_id for chunk_id in ids)
    assert not any("-v1-chunk-" in chunk_id for chunk_id in ids)


def test_chunk_metadata_is_complete(
    indexing_service: IndexingService, vector_store: VectorStoreService
) -> None:
    indexing_service.index(build_request())
    stored = stored_chunks(vector_store, 7)

    assert stored["metadatas"]
    for metadata in stored["metadatas"]:
        assert metadata["documentId"] == 7
        assert metadata["category"] == "RECRUITMENT_PROCEDURE"
        assert metadata["version"] == 1
        assert metadata["active"] is True
        assert metadata["title"] == "Processus de recrutement"
        assert isinstance(metadata["chunkIndex"], int)
        assert metadata["indexedAt"]


def test_personal_data_is_not_stored_in_chunks(
    indexing_service: IndexingService, vector_store: VectorStoreService
) -> None:
    response = indexing_service.index(
        build_request(
            content=(
                "Contactez jean.dupont@example.com ou le 06 12 34 56 78. "
                "Le processus de recrutement reste inchangé."
            )
        )
    )
    stored = stored_chunks(vector_store, 7)

    assert response.anonymized_items_count >= 2
    assert "jean.dupont@example.com" not in " ".join(stored["documents"])
    assert "06 12 34 56 78" not in " ".join(stored["documents"])
    assert "[EMAIL]" in " ".join(stored["documents"])


def test_delete_document_is_idempotent(
    indexing_service: IndexingService, vector_store: VectorStoreService
) -> None:
    indexing_service.index(build_request())
    first = vector_store.delete_document(7)
    second = vector_store.delete_document(7)

    assert first > 0
    assert second == 0
    assert vector_store.count_document_chunks(7) == 0


def test_chunks_do_not_retain_previous_version_after_deactivation_and_reactivation(
    indexing_service: IndexingService, vector_store: VectorStoreService
) -> None:
    indexing_service.index(build_request(version=1))
    indexing_service.index(build_request(version=2, active=False))
    assert vector_store.count_document_chunks(7) == 0

    indexing_service.index(build_request(version=2, active=True))
    ids = stored_chunks(vector_store, 7)["ids"]
    assert ids
    assert all("-v2-chunk-" in chunk_id for chunk_id in ids)
