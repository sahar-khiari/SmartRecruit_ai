from fastapi.testclient import TestClient

from app.models.knowledge import KnowledgeDocumentIndexRequest
from app.services.indexing_service import IndexingService


def test_index_and_search_http_contract(
    client: TestClient, indexing_service: IndexingService, auth_headers
) -> None:
    index_payload = {
        "documentId": 12,
        "title": "Processus de recrutement",
        "content": "Le processus de recrutement comporte un entretien technique.",
        "category": "RECRUITMENT_PROCEDURE",
        "version": 1,
        "active": True,
    }
    index_response = client.post(
        "/api/v1/knowledge/index", json=index_payload, headers=auth_headers
    )

    assert index_response.status_code == 200
    indexed = index_response.json()
    assert indexed["documentId"] == 12
    assert indexed["status"] == "INDEXED"
    assert indexed["chunksIndexed"] >= 1
    assert indexed["anonymizedItemsCount"] == 0
    assert indexed["indexedAt"]

    search_response = client.post(
        "/api/v1/retrieval/search",
        json={
            "query": "Comment se déroule un recrutement ?",
            "topK": 5,
            "categories": ["RECRUITMENT_PROCEDURE"],
        },
        headers=auth_headers,
    )

    assert search_response.status_code == 200
    result = search_response.json()
    assert result["query"] == "Comment se déroule un recrutement ?"
    assert result["sanitizedQuery"] == "Comment se déroule un recrutement ?"
    assert result["totalResults"] >= 1
    assert result["modelName"] == "fake-multilingual-local-model"
    assert result["results"][0]["documentId"] == 12
    assert result["results"][0]["chunkId"].startswith("document-12-v1-chunk-")
    assert result["results"][0]["category"] == "RECRUITMENT_PROCEDURE"
    assert result["results"][0]["score"] > 0
    assert "answer" not in result


def test_delete_http_contract(
    client: TestClient, indexing_service: IndexingService, auth_headers
) -> None:
    indexing_service.index(
        KnowledgeDocumentIndexRequest(
            documentId=12,
            title="À supprimer",
            content="Le processus de recrutement est décrit ici.",
            category="FAQ",
            version=1,
            active=True,
        )
    )

    response = client.delete("/api/v1/knowledge/12", headers=auth_headers)
    assert response.status_code == 200
    body = response.json()
    assert body["documentId"] == 12
    assert body["deletedChunks"] > 0
    assert body["status"] == "DELETED"

    second = client.delete("/api/v1/knowledge/12", headers=auth_headers)
    assert second.status_code == 200
    assert second.json()["deletedChunks"] == 0


def test_empty_query_is_rejected(client: TestClient, auth_headers) -> None:
    response = client.post(
        "/api/v1/retrieval/search",
        json={"query": "   ", "topK": 5},
        headers=auth_headers,
    )
    assert response.status_code == 422


def test_out_of_bounds_top_k_is_rejected(client: TestClient, auth_headers) -> None:
    response = client.post(
        "/api/v1/retrieval/search",
        json={"query": "recrutement", "topK": 99},
        headers=auth_headers,
    )
    assert response.status_code == 422


def test_invalid_document_id_is_rejected(client: TestClient, auth_headers) -> None:
    response = client.post(
        "/api/v1/knowledge/index",
        json={
            "documentId": 0,
            "title": "Titre",
            "content": "Contenu",
            "category": "FAQ",
            "version": 0,
            "active": True,
        },
        headers=auth_headers,
    )
    assert response.status_code == 422


def test_chat_returns_controlled_fallback_without_context(
    client: TestClient, auth_headers
) -> None:
    response = client.post(
        "/api/v1/chat/generate",
        json={"question": "Comment postuler ?", "userRole": "CANDIDATE"},
        headers=auth_headers,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["answer"].startswith("Je ne dispose pas")
    assert body["category"] == "OTHER"
    assert body["grounded"] is False
    assert body["needsHumanReview"] is True
    assert body["sources"] == []
    assert body["modelName"] == "qwen3:4b"
    assert body["promptVersion"] == "hr-rag-v1"
    assert body["retrievalDurationMs"] >= 0
    assert body["generationDurationMs"] == 0
    assert body["totalDurationMs"] >= 0
    assert "test-internal-key" not in response.text


def test_chat_rejects_client_controlled_fields(
    client: TestClient, auth_headers
) -> None:
    response = client.post(
        "/api/v1/chat/generate",
        json={
            "question": "Comment postuler ?",
            "userRole": "CANDIDATE",
            "userId": 12,
            "modelName": "autre-modele",
        },
        headers=auth_headers,
    )

    assert response.status_code == 422


def test_chat_rejects_invalid_role_and_top_k(
    client: TestClient, auth_headers
) -> None:
    invalid_role = client.post(
        "/api/v1/chat/generate",
        json={"question": "Comment postuler ?", "userRole": "ADMIN"},
        headers=auth_headers,
    )
    invalid_top_k = client.post(
        "/api/v1/chat/generate",
        json={"question": "Comment postuler ?", "userRole": "CANDIDATE", "topK": 9},
        headers=auth_headers,
    )

    assert invalid_role.status_code == 422
    assert invalid_top_k.status_code == 422


def test_openapi_documents_all_internal_endpoints(client: TestClient) -> None:
    schema = client.get("/openapi.json").json()
    paths = schema["paths"]
    assert "/health" in paths
    assert "/api/v1/knowledge/index" in paths
    assert "/api/v1/knowledge/{document_id}" in paths
    assert "/api/v1/retrieval/search" in paths
    assert "/api/v1/chat/generate" in paths
    assert "X-AI-Service-Key" in schema["components"]["securitySchemes"][
        next(iter(schema["components"]["securitySchemes"]))
    ]["name"]
