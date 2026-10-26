"""Comparaison IA d'offres (Module 2) : Cohere simulé, aucun appel réseau."""

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr, ValidationError

from app.core.config import Settings, get_settings
from app.main import create_app
from app.offres.cohere_client import CohereInvalidOutputError, CohereQuotaError
from app.offres.comparaison import ComparaisonRequest, ComparaisonService, get_comparaison_service

KEY = "test-internal-key"

OFFRES = [
    {"id": 1, "titrePoste": "Développeur Java Spring Boot", "niveauRequis": "Junior", "typeContrat": "CDI",
     "localisation": "Tunis", "competencesRequises": "Java, Spring Boot, SQL",
     "description": "Développement d'API backend et tests unitaires. Contact : rh@exemple.com"},
    {"id": 2, "titrePoste": "Développeur Full-Stack Angular/Java", "niveauRequis": "Intermédiaire",
     "typeContrat": "CDI", "localisation": "Tunis", "competencesRequises": "Java, Angular, TypeScript",
     "description": "Développement backend et interfaces Angular."},
]

LLM_OK = {
    "offres": [
        {"offre_id": 1, "missions": "APIs backend et tests", "competences_cles": ["Java", "Spring Boot", "Kubernetes"],
         "points_forts": ["Adaptée à un junior", "Centrée backend"], "profil_ideal": "Junior backend"},
        {"offre_id": 2, "missions": "Backend et interfaces Angular", "competences_cles": ["Java", "Angular"],
         "points_forts": ["Plus variée, front et back"], "profil_ideal": "Développeur intermédiaire"},
    ],
    "recommandation": {"offre_id": 1, "justification": "Avec votre profil junior Java/Spring, l'offre 1 correspond mieux."},
}


class FakeCohere:
    def __init__(self, responses):
        self.responses = list(responses)
        self.prompts = []

    def chat_json(self, system, user, temperature=0.2):
        self.prompts.append(user)
        response = self.responses.pop(0) if len(self.responses) > 1 else self.responses[0]
        if isinstance(response, Exception):
            raise response
        return response


def compare(fake, profil="Junior, je connais Java et Spring. alice@mail.com 22 333 444"):
    return ComparaisonService(fake, max_retries=1).comparer(
        ComparaisonRequest.model_validate({"offres": OFFRES, "profil": profil}))


def test_facts_come_from_offers_not_from_llm():
    result = compare(FakeCohere([LLM_OK]))
    a, b = result.offres
    assert (a.niveau, a.contrat, a.lieu) == ("Junior", "CDI", "Tunis")
    assert b.niveau == "Intermédiaire"
    assert result.recommandation.offre_id == 1
    assert result.profil_utilise is True


def test_llm_cannot_add_skills_absent_from_offer():
    a = compare(FakeCohere([LLM_OK])).offres[0]
    assert "Kubernetes" not in a.competences_cles
    assert a.competences_cles == ["Java", "Spring Boot"]


def test_profile_and_offers_are_anonymized_before_cohere():
    fake = FakeCohere([LLM_OK])
    compare(fake)
    prompt = fake.prompts[0]
    assert "alice@mail.com" not in prompt and "22 333 444" not in prompt
    assert "rh@exemple.com" not in prompt
    assert "[EMAIL]" in prompt


def test_no_profile_means_no_recommended_offer():
    result = compare(FakeCohere([LLM_OK]), profil=None)
    assert result.profil_utilise is False
    assert result.recommandation.offre_id is None


def test_unknown_recommended_offer_is_ignored():
    bad = {**LLM_OK, "recommandation": {"offre_id": 99, "justification": "L'offre 99 est parfaite pour vous."}}
    assert compare(FakeCohere([bad])).recommandation.offre_id is None


def test_invalid_output_is_retried_then_fails():
    fake = FakeCohere([{"oups": True}, LLM_OK])
    assert compare(fake).offres[0].offre_id == 1
    with pytest.raises(CohereInvalidOutputError):
        compare(FakeCohere([{"oups": True}]))


def test_request_needs_two_or_three_distinct_offers():
    with pytest.raises(ValidationError):
        ComparaisonRequest.model_validate({"offres": OFFRES[:1]})
    with pytest.raises(ValidationError):
        ComparaisonRequest.model_validate({"offres": [OFFRES[0], OFFRES[0]]})


@pytest.fixture
def client():
    app = create_app()
    app.dependency_overrides[get_settings] = lambda: Settings(ai_service_api_key=SecretStr(KEY))
    fake = FakeCohere([LLM_OK])
    app.dependency_overrides[get_comparaison_service] = lambda: ComparaisonService(fake, max_retries=0)
    with TestClient(app) as test_client:
        yield test_client, fake


def test_endpoint_returns_camel_case_and_requires_key(client):
    test_client, _ = client
    assert test_client.post("/api/v1/offres/ia/comparer", json={"offres": OFFRES}).status_code == 401

    response = test_client.post("/api/v1/offres/ia/comparer", headers={"X-AI-Service-Key": KEY},
                                json={"offres": OFFRES, "profil": "Junior Java"})
    assert response.status_code == 200
    body = response.json()
    assert body["offres"][0]["competencesCles"] == ["Java", "Spring Boot"]
    assert body["recommandation"]["offreId"] == 1


def test_endpoint_translates_cohere_errors(client):
    test_client, fake = client
    fake.responses = [CohereQuotaError("quota")]
    response = test_client.post("/api/v1/offres/ia/comparer", headers={"X-AI-Service-Key": KEY},
                                json={"offres": OFFRES})
    assert response.status_code == CohereQuotaError("x").status_code
