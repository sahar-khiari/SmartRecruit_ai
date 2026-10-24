from fastapi.testclient import TestClient

VALID_DOCUMENT = {
    "documentId": 1,
    "title": "Processus de recrutement",
    "content": "Le processus de recrutement comporte plusieurs étapes.",
    "category": "RECRUITMENT_PROCEDURE",
    "version": 1,
    "active": True,
}


def test_index_requires_api_key(client: TestClient) -> None:
    response = client.post("/api/v1/knowledge/index", json=VALID_DOCUMENT)
    assert response.status_code == 401
    assert "Clé interne" in response.json()["detail"]


def test_index_rejects_wrong_api_key(client: TestClient) -> None:
    response = client.post(
        "/api/v1/knowledge/index",
        json=VALID_DOCUMENT,
        headers={"X-AI-Service-Key": "wrong-key"},
    )
    assert response.status_code == 403


def test_index_accepts_correct_api_key(client: TestClient, auth_headers) -> None:
    response = client.post(
        "/api/v1/knowledge/index", json=VALID_DOCUMENT, headers=auth_headers
    )
    assert response.status_code == 200
    assert response.json()["status"] == "INDEXED"
    assert "test-internal-key" not in response.text


def test_search_requires_api_key(client: TestClient) -> None:
    response = client.post("/api/v1/retrieval/search", json={"query": "recrutement"})
    assert response.status_code == 401


def test_delete_requires_api_key(client: TestClient) -> None:
    response = client.delete("/api/v1/knowledge/1")
    assert response.status_code == 401


def test_chat_requires_api_key(client: TestClient) -> None:
    response = client.post(
        "/api/v1/chat/generate",
        json={"question": "Comment postuler ?", "userRole": "CANDIDATE"},
    )
    assert response.status_code == 401


def test_chat_rejects_wrong_api_key(client: TestClient) -> None:
    response = client.post(
        "/api/v1/chat/generate",
        json={"question": "Comment postuler ?", "userRole": "CANDIDATE"},
        headers={"X-AI-Service-Key": "wrong-key"},
    )
    assert response.status_code == 403


def test_no_cors_access_for_angular_origin(client: TestClient) -> None:
    response = client.options(
        "/api/v1/retrieval/search",
        headers={
            "Origin": "http://localhost:4200",
            "Access-Control-Request-Method": "POST",
        },
    )
    assert "access-control-allow-origin" not in {
        key.lower() for key in response.headers
    }


def test_unknown_properties_are_rejected(client: TestClient, auth_headers) -> None:
    payload = {**VALID_DOCUMENT, "userId": 42}
    response = client.post(
        "/api/v1/knowledge/index", json=payload, headers=auth_headers
    )
    assert response.status_code == 422


def test_health_remains_accessible_without_key(client: TestClient) -> None:
    assert client.get("/health").status_code == 200
