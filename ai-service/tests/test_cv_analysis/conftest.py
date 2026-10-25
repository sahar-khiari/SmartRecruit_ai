"""Fixtures des tests du Module 1. Placées dans tests/test_cv_analysis/ pour ne pas toucher
au tests/conftest.py commun lors de l'intégration."""

from collections.abc import Callable

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.core.config import Settings, get_settings
from app.coreAgents.Agents.cv_analysis.extractor import CvExtractor
from app.coreAgents.Agents.cv_analysis.pipeline import CvAnalysisPipeline
from app.main import create_app
from app.v1.endpoints.cv_analysis import get_cv_pipeline

from .helpers import TEST_API_KEY, VALID_EXTRACTION, FakeLlmClient, build_pdf


@pytest.fixture
def make_pdf() -> Callable[..., bytes]:
    return build_pdf


@pytest.fixture
def llm_clients() -> list[FakeLlmClient]:
    """Modèles simulés utilisés par l'API dans les tests ; un test peut remplacer leurs réponses."""
    return [FakeLlmClient("fake-principal", [VALID_EXTRACTION]),
            FakeLlmClient("fake-secours", [VALID_EXTRACTION])]


@pytest.fixture
def client(llm_clients) -> TestClient:
    app = create_app()
    app.dependency_overrides[get_settings] = lambda: Settings(ai_service_api_key=SecretStr(TEST_API_KEY))
    app.dependency_overrides[get_cv_pipeline] = lambda: CvAnalysisPipeline(CvExtractor(llm_clients))
    with TestClient(app) as test_client:
        yield test_client
