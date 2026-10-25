"""Tests du Module 2 (IA des offres) avec un faux client Cohere : aucun appel réseau."""
from typing import Any

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.offres import router as offres_router
from app.offres.cohere_client import CohereClient, CohereInvalidOutputError, CohereKeyMissingError
from app.offres.config import OffresSettings
from app.offres.referentiel_competences import COMPETENCES
from app.offres.schemas import CompetencesRequest, DescriptionRequest, MotsClesRequest
from app.offres.service import OffresIaService, get_offres_service, mots_cles_baseline

INTERNAL_KEY = {"X-AI-Service-Key": "smartrecruit-local-development-key"}

DESCRIPTION_OK = {
    "accroche": "Rejoignez une équipe produit dynamique pour construire des API robustes.",
    "missions": ["Concevoir des API REST", "Écrire des tests", "Participer aux revues de code"],
    "profil": ["Maîtrise de Java", "Connaissance de Spring Boot", "Esprit d'équipe"],
    "avantages": ["Télétravail partiel", "Formation continue"],
}


class FakeCohere:
    """Remplace CohereClient : renvoie des réponses préparées et compte les appels."""

    def __init__(self, chat_responses: list[Any]) -> None:
        self.chat_responses = list(chat_responses)
        self.chat_calls = 0
        self.embed_calls = 0
        self.last_user_prompt = ""

    def chat_json(self, system_prompt: str, user_prompt: str, temperature: float = 0.2) -> dict:
        self.chat_calls += 1
        self.last_user_prompt = user_prompt
        response = self.chat_responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response

    def embed(self, texts: list[str], input_type: str) -> list[list[float]]:
        self.embed_calls += 1
        if input_type == "search_query":
            return [[1.0, 0.0]]
        # Java et Spring Boot proches de l'offre, le reste éloigné.
        vecteurs = []
        for texte in texts:
            proche = texte.startswith("Java ") or texte.startswith("Spring Boot")
            vecteurs.append([1.0, 0.1] if proche else [0.0, 1.0])
        return vecteurs


def make_service(fake: FakeCohere, retries: int = 2) -> OffresIaService:
    settings = OffresSettings(cohere_api_key="test-key", cohere_max_retries=retries)
    return OffresIaService(settings, fake)  # type: ignore[arg-type]


# ------------------------------------------------------------------ service

def test_description_valide_et_composee():
    fake = FakeCohere([DESCRIPTION_OK])
    resultat = make_service(fake).generer_description(DescriptionRequest(titrePoste="Développeur Java"))
    assert "Missions :" in resultat.description
    assert resultat.missions[0] == "Concevoir des API REST"
    assert resultat.depuis_cache is False


def test_description_mise_en_cache():
    fake = FakeCohere([DESCRIPTION_OK])
    service = make_service(fake)
    req = DescriptionRequest(titrePoste="Développeur Java")
    service.generer_description(req)
    second = service.generer_description(req)
    assert second.depuis_cache is True
    assert fake.chat_calls == 1


def test_sortie_invalide_reessayee_puis_acceptee():
    fake = FakeCohere([{"accroche": "x"}, DESCRIPTION_OK])
    resultat = make_service(fake).generer_description(DescriptionRequest(titrePoste="Dev Java"))
    assert fake.chat_calls == 2
    assert len(resultat.profil) == 3


def test_sortie_toujours_invalide_leve_une_erreur():
    fake = FakeCohere([{"oups": 1}, {"oups": 2}])
    with pytest.raises(CohereInvalidOutputError):
        make_service(fake, retries=1).generer_description(DescriptionRequest(titrePoste="Dev Java"))


def test_donnees_personnelles_anonymisees_avant_envoi():
    fake = FakeCohere([DESCRIPTION_OK])
    make_service(fake).generer_description(DescriptionRequest(
        titrePoste="Développeur Java", notes="Contact : rh@techcorp.tn ou +216 22 333 444"))
    assert "rh@techcorp.tn" not in fake.last_user_prompt
    assert "22 333 444" not in fake.last_user_prompt


def test_competences_embedding_puis_llm():
    fake = FakeCohere([{"competences": ["Java", "Spring Boot", "Kafka"]}])
    resultat = make_service(fake).suggerer_competences(
        CompetencesRequest(titrePoste="Développeur Java Spring", description="API REST"))
    noms = [c.nom for c in resultat.competences]
    assert noms == ["Java", "Spring Boot", "Kafka"]
    assert resultat.competences[0].source == "embedding+llm"
    assert resultat.competences[2].source == "llm"
    assert resultat.methode == "embedding+llm"


def test_competences_repli_sur_embeddings_si_llm_invalide():
    fake = FakeCohere([{"rien": True}, {"rien": True}, {"rien": True}])
    resultat = make_service(fake).suggerer_competences(CompetencesRequest(titrePoste="Dev Java"))
    assert resultat.methode == "embedding"
    assert resultat.competences[0].nom in {"Java", "Spring Boot"}


def test_vecteurs_du_referentiel_calcules_une_seule_fois():
    fake = FakeCohere([{"competences": ["Java"]}, {"competences": ["Java"]}])
    service = make_service(fake)
    service.suggerer_competences(CompetencesRequest(titrePoste="Dev Java"))
    service.suggerer_competences(CompetencesRequest(titrePoste="Dev Java senior"))
    # 1 appel pour le référentiel + 1 par offre
    assert fake.embed_calls == 3
    assert len(COMPETENCES) > 100


def test_mots_cles_tries_et_dedoublonnes():
    fake = FakeCohere([{"mots_cles": [
        {"mot": "Agile", "importance": 2},
        {"mot": "Java", "importance": 5},
        {"mot": "java", "importance": 4},
    ]}])
    resultat = make_service(fake).detecter_mots_cles(MotsClesRequest(
        titrePoste="Dev Java", description="Développement Java en méthode Agile avec Spring Boot."))
    assert [m.mot for m in resultat.mots_cles] == ["Java", "Agile"]
    assert "java" in resultat.mots_cles_baseline


def test_baseline_ignore_les_mots_vides():
    mots = mots_cles_baseline("Nous recherchons un développeur Java pour des API Java et Docker")
    assert mots[0] == "java"
    assert "nous" not in mots and "pour" not in mots


def test_cle_absente():
    client = CohereClient(OffresSettings(cohere_api_key=None))
    with pytest.raises(CohereKeyMissingError):
        client.chat_json("s", "u")


def test_cle_placeholder_consideree_absente():
    assert OffresSettings(cohere_api_key="REMPLACE_PAR_TA_CLE_COHERE").cohere_key_value is None


# ------------------------------------------------------------------- router

@pytest.fixture
def api():
    app = FastAPI()
    app.include_router(offres_router.router)
    fake = FakeCohere([DESCRIPTION_OK])
    app.dependency_overrides[get_offres_service] = lambda: make_service(fake)
    app.dependency_overrides[get_settings] = get_settings
    return TestClient(app)


def test_endpoint_exige_la_cle_interne(api):
    response = api.post("/api/v1/offres/ia/description", json={"titrePoste": "Dev Java"})
    assert response.status_code == 401


def test_endpoint_description_camel_case(api):
    response = api.post("/api/v1/offres/ia/description", json={"titrePoste": "Dev Java"},
                        headers=INTERNAL_KEY)
    assert response.status_code == 200
    body = response.json()
    assert body["versionPrompt"] == "offres-v1"
    assert body["depuisCache"] is False


def test_endpoint_traduit_les_erreurs_cohere():
    app = FastAPI()
    app.include_router(offres_router.router)
    app.dependency_overrides[get_offres_service] = lambda: make_service(
        FakeCohere([CohereKeyMissingError("Clé Cohere absente")]))
    response = TestClient(app).post("/api/v1/offres/ia/description", json={"titrePoste": "Dev Java"},
                                    headers=INTERNAL_KEY)
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "COHERE_KEY_MISSING"
