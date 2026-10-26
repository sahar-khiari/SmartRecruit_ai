from app.coreAgents.Agents.cv_analysis.llm.base import LlmUnavailableError
from .helpers import FICTIONAL_CV, TEST_API_KEY

URL = "/api/v1/cv/analyze"
HEADERS = {"X-AI-Service-Key": TEST_API_KEY}


def _upload(client, content: bytes, headers=HEADERS):
    return client.post(URL, headers=headers, files={"file": ("cv.pdf", content, "application/pdf")})


def test_analyze_returns_anonymized_text_only(client, make_pdf):
    response = _upload(client, make_pdf(FICTIONAL_CV))

    assert response.status_code == 200
    body = response.json()
    assert body["pipeline_version"] == "cv-pipeline-v3"
    assert body["page_count"] == 1
    assert "Spring Boot" in body["anonymized_text"]
    assert "ahmed.bensalah@example.com" not in response.text
    assert "Ahmed" not in response.text
    assert {"EMAIL", "PHONE", "NAME"} <= set(body["anonymization"]["categories"])
    assert set(body["durations_ms"]) == {"pdf_read", "anonymization", "extraction", "normalization"}


def test_analyze_returns_profile_proposal_and_llm_trace(client, make_pdf, llm_clients):
    body = _upload(client, make_pdf(FICTIONAL_CV)).json()

    assert body["profile"]["skills"][:2] == ["Java", "Spring Boot"]
    assert body["profile"]["skills_details"][0] == {
        "original": "Java", "name": "Java", "category": "LANGUAGE", "method": "EXACT", "score": 100}
    assert body["profile"]["skills_referential_version"] == "skills-ref-v1"
    assert body["profile"]["years_of_experience"] == 3
    assert body["profile"]["experience_level"] == "INTERMEDIATE"
    assert body["llm"]["model"] == "fake-principal"
    assert body["llm"]["fallback_used"] is False
    assert body["llm"]["prompt_version"] == "cv-extract-v1"
    # Le LLM ne reçoit que le texte anonymisé.
    assert "ahmed.bensalah@example.com" not in llm_clients[0].calls[0]
    assert "Ahmed" not in llm_clients[0].calls[0]


def test_all_models_down_returns_503(client, make_pdf, llm_clients):
    for fake in llm_clients:
        fake._responses = [LlmUnavailableError("503")]

    response = _upload(client, make_pdf(FICTIONAL_CV))

    assert response.status_code == 503
    assert response.json()["code"] == "LLM_UNAVAILABLE"


def test_requires_internal_key(client, make_pdf):
    assert _upload(client, make_pdf(FICTIONAL_CV), headers={}).status_code == 401
    assert _upload(client, make_pdf(FICTIONAL_CV), headers={"X-AI-Service-Key": "mauvaise"}).status_code == 403


def test_rejects_non_pdf_with_code(client):
    response = _upload(client, b"pas un pdf du tout")

    assert response.status_code == 422
    assert response.json()["code"] == "NOT_PDF"


def test_rejects_too_large(client):
    response = _upload(client, b"%PDF-" + b"0" * (5 * 1024 * 1024))

    assert response.status_code == 413
    assert response.json()["code"] == "TOO_LARGE"


def test_missing_file_is_rejected(client):
    assert client.post(URL, headers=HEADERS).status_code == 422


def test_health_is_public(client):
    # Le contenu de /health dépend du projet (commun ou indépendant) : on vérifie seulement l'accès sans clé.
    assert client.get("/health").status_code == 200
