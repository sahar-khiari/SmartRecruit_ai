"""
Tests unitaires du endpoint embed-rank (Layer 1).
Utilise des mocks pour éviter de charger le modèle réel.
"""
import pytest
from unittest.mock import patch, MagicMock
import numpy as np
from fastapi.testclient import TestClient


def make_app():
    """Crée une mini-app FastAPI avec uniquement le routeur matching."""
    from fastapi import FastAPI
    from app.matching.router import router
    app = FastAPI()

    # Mock de la sécurité pour les tests
    from app.core.security import require_internal_api_key
    app.dependency_overrides[require_internal_api_key] = lambda: None

    app.include_router(router)
    return app


@pytest.fixture
def client():
    app = make_app()
    return TestClient(app)


def test_embed_rank_unavailable_when_model_fails(client):
    """Si le modèle d'embedding échoue, retourne available=False (fallback Layer 0)."""
    with patch("app.matching.embedding_service.embed_texts", return_value=(False, None)):
        response = client.post("/api/v1/matching/embed-rank", json={
            "offre_texte": "Développeur Java Spring Boot",
            "candidates": [
                {"candidate_id": 1, "cv_text": "Java Spring Boot Docker"},
                {"candidate_id": 2, "cv_text": "Python Django Flask"},
            ]
        })
    assert response.status_code == 200
    data = response.json()
    assert data["available"] is False
    assert "error_message" in data


def test_embed_rank_returns_sorted_results(client):
    """Résultats triés par score décroissant quand embeddings disponibles."""
    # Vecteur offre similaire au candidat 1
    mock_embeddings = np.array([
        [1.0, 0.0, 0.0],   # offre
        [0.95, 0.1, 0.0],  # candidat 1 (similaire)
        [0.0, 0.0, 1.0],   # candidat 2 (différent)
    ], dtype=np.float32)

    with patch("app.matching.embedding_service.embed_texts",
               return_value=(True, mock_embeddings)):
        response = client.post("/api/v1/matching/embed-rank", json={
            "offre_texte": "Développeur Java Spring Boot",
            "candidates": [
                {"candidate_id": 1, "cv_text": "Java Spring Boot Docker"},
                {"candidate_id": 2, "cv_text": "Python machine learning pandas"},
            ]
        })

    assert response.status_code == 200
    data = response.json()
    assert data["available"] is True
    assert len(data["results"]) == 2
    # Le meilleur candidat doit être en tête
    assert data["results"][0]["score"] >= data["results"][1]["score"]


def test_llm_judge_disabled_by_default(client):
    """Le LLM-judge retourne available=False si désactivé (comportement par défaut)."""
    with patch("app.matching.config.get_matching_settings") as mock_settings:
        mock_settings.return_value = MagicMock(
            matching_llm_enabled=False,
        )
        response = client.post("/api/v1/matching/llm-judge", json={
            "cv_text_anonymise": "Développeur Java 5 ans",
            "offre_texte": "Poste Java Spring Boot",
            "niveau_requis": "INTERMEDIAIRE",
            "criteres_noms": ["java", "spring boot"],
            "prompt_version": "matching-v1",
        })

    assert response.status_code == 200
    data = response.json()
    assert data["available"] is False
    assert "désactivé" in data.get("error_message", "")


def test_embed_rank_validates_input(client):
    """Requête invalide → 422."""
    response = client.post("/api/v1/matching/embed-rank", json={
        "offre_texte": "",
        # candidates manquant
    })
    assert response.status_code == 422
