"""
Tests de l'évaluateur et de l'endpoint FastAPI — Module 4.
Auteur : Aziz (module4-aziz)

Ce qu'on teste :
  1. L'évaluateur corrige correctement les réponses.
  2. Il lève TechEvaluationError si le test est introuvable.
  3. L'endpoint POST /generate retourne 201 avec un test valide.
  4. L'endpoint retourne 503 si le LLM est indisponible.
  5. L'endpoint refuse les données invalides (422 Unprocessable Entity).

Tests corrigés après génération IA :
  - Le TestClient FastAPI nécessite d'utiliser app.dependency_overrides
    pour injecter les mocks; simplifié en mockant les classes directement.
  - Ajout du test 422 pour les inputs invalides (oublié dans la génération initiale).
"""

from unittest.mock import MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from app.coreAgents.Agents.tech_assessment.evaluator import (
    TechEvaluationError,
    TechTestEvaluator,
)
from app.v1.schemas.tech_assessment import (
    EvaluationResult,
    SubmitTestRequest,
    SubmitTestResponse,
    ReponseQuestion,
)


# ---------------------------------------------------------------------------
# Fixture : réponse LLM valide pour l'évaluation
# ---------------------------------------------------------------------------

@pytest.fixture
def reponse_evaluation_llm() -> dict:
    return {
        "score": 75,
        "score_questions": 80,
        "score_exercice": 60,
        "points_forts": ["Bonne maîtrise des bases Docker", "Dockerfile bien structuré"],
        "points_faibles": ["docker-compose peu maîtrisé"],
        "feedback": (
            "Bon niveau général sur Docker. La commande docker run est bien connue. "
            "Travaillez docker-compose pour les architectures multi-conteneurs."
        ),
        "recommandation": "PASSER",
    }


@pytest.fixture
def test_data_bdd() -> dict:
    """Simule le test_data stocké en BDD (avec réponses correctes)."""
    return {
        "titre": "Test DevOps Junior",
        "job_title": "DevOps Junior",
        "duree_minutes": 45,
        "questions": [
            {
                "numero": 1, "type": "qcm",
                "enonce": "Qu'est-ce que Docker ?",
                "competence_ciblee": "Docker",
                "choix": [
                    {"lettre": "A", "texte": "Un OS"},
                    {"lettre": "B", "texte": "Conteneurisation"},
                    {"lettre": "C", "texte": "Langage"},
                    {"lettre": "D", "texte": "Cloud"},
                ],
                "reponse_correcte": "B",
                "points": 2,
            },
            {
                "numero": 2, "type": "ouverte",
                "enonce": "Expliquez docker-compose.",
                "competence_ciblee": "Docker",
                "reponse_correcte": "Orchestration multi-conteneurs.",
                "points": 4,
            },
        ],
        "exercice": {
            "enonce": "Écrivez un Dockerfile.",
            "competence_ciblee": "Docker",
            "criteres_correction": ["FROM python:3.11-slim", "EXPOSE 5000"],
            "points": 10,
        },
        "instructions": "Répondez à chaque question.",
    }


@pytest.fixture
def requete_soumission() -> SubmitTestRequest:
    return SubmitTestRequest(
        candidate_id="cand-anonyme-001",
        reponses_questions=[
            ReponseQuestion(numero=1, reponse="B"),
            ReponseQuestion(numero=2, reponse="Outil pour lancer plusieurs conteneurs ensemble."),
        ],
        reponse_exercice="FROM python:3.11-slim\nCOPY . .\nRUN pip install -r requirements.txt\nEXPOSE 5000\nCMD ['python', 'app.py']",
    )


# ---------------------------------------------------------------------------
# Tests : TechTestEvaluator
# ---------------------------------------------------------------------------

class TestTechTestEvaluator:

    def _creer_evaluateur_mocke(
        self, reponse_llm: dict, test_data: dict
    ) -> TechTestEvaluator:
        """Crée un évaluateur avec LLM et BDD mockés."""
        llm_mock = MagicMock()
        llm_mock.chat_json.return_value = reponse_llm

        # Mock du modèle BDD
        modele_bdd_mock = MagicMock()
        modele_bdd_mock.test_data = test_data

        session_mock = MagicMock()
        session_mock.query.return_value.filter.return_value.first.return_value = modele_bdd_mock
        session_factory_mock = MagicMock(return_value=session_mock)

        return TechTestEvaluator(
            session_factory=session_factory_mock,
            llm_client=llm_mock,
        )

    def test_evaluation_reussie(
        self, reponse_evaluation_llm, test_data_bdd, requete_soumission
    ):
        evaluateur = self._creer_evaluateur_mocke(reponse_evaluation_llm, test_data_bdd)
        result = evaluateur.evaluate(
            test_id="550e8400-e29b-41d4-a716-446655440000",
            request=requete_soumission,
        )
        assert isinstance(result, SubmitTestResponse)
        assert result.evaluation.score == 75
        assert result.evaluation.recommandation == "PASSER"

    def test_test_introuvable_leve_erreur(self, reponse_evaluation_llm, requete_soumission):
        llm_mock = MagicMock()
        session_mock = MagicMock()
        # BDD retourne None (test pas trouvé)
        session_mock.query.return_value.filter.return_value.first.return_value = None
        session_factory_mock = MagicMock(return_value=session_mock)

        evaluateur = TechTestEvaluator(
            session_factory=session_factory_mock,
            llm_client=llm_mock,
        )

        with pytest.raises(TechEvaluationError, match="introuvable"):
            evaluateur.evaluate(
                test_id="550e8400-e29b-41d4-a716-446655440000",
                request=requete_soumission,
            )

    def test_llm_indisponible_leve_erreur(self, test_data_bdd, requete_soumission):
        from app.coreAgents.llm_client import LLMError

        llm_mock = MagicMock()
        llm_mock.chat_json.side_effect = LLMError("Quota dépassé")

        modele_bdd_mock = MagicMock()
        modele_bdd_mock.test_data = test_data_bdd
        session_mock = MagicMock()
        session_mock.query.return_value.filter.return_value.first.return_value = modele_bdd_mock
        session_factory_mock = MagicMock(return_value=session_mock)

        evaluateur = TechTestEvaluator(
            session_factory=session_factory_mock,
            llm_client=llm_mock,
        )

        with pytest.raises(TechEvaluationError, match="indisponible"):
            evaluateur.evaluate(
                test_id="550e8400-e29b-41d4-a716-446655440000",
                request=requete_soumission,
            )


# ---------------------------------------------------------------------------
# Tests : Endpoints FastAPI (tests d'intégration avec TestClient)
# ---------------------------------------------------------------------------

class TestEndpointGenerate:
    """Tests de l'endpoint POST /api/v1/tests/generate."""

    @pytest.fixture(autouse=True)
    def client(self):
        from app.main import app
        self.client = TestClient(app)

    def test_input_invalide_retourne_422(self):
        """FastAPI doit rejeter les données invalides avec 422."""
        response = self.client.post(
            "/api/v1/tests/generate",
            json={
                "job_title": "",  # titre vide → invalide
                "job_description": "Description trop courte",
                "competences_manquantes": [],  # vide → invalide
                "candidate_id": "cand-001",
            },
        )
        assert response.status_code == 422

    def test_version_prompt_invalide_retourne_422(self):
        """Un prompt_version autre que v1/v2/v3 doit être rejeté."""
        response = self.client.post(
            "/api/v1/tests/generate?prompt_version=v99",
            json={
                "job_title": "DevOps",
                "job_description": "Description valide du poste DevOps.",
                "competences_manquantes": [{"skill": "Docker", "level": "intermédiaire"}],
                "candidate_id": "cand-001",
            },
        )
        assert response.status_code == 422

    @patch("app.v1.endpoints.tech_assessment.TechTestGenerator")
    def test_generation_reussie_retourne_201(self, MockGenerator, reponse_llm_valide=None):
        """Avec un générateur mocké, l'endpoint doit retourner 201."""
        from app.v1.schemas.tech_assessment import (
            ExercicePratique, Question, TechTest, TechTestPublic, TypeQuestion, ChoixQCM
        )

        # Construire un TechTestPublic factice
        questions = []
        for i in range(1, 6):
            q = Question(
                numero=i,
                type=TypeQuestion.QCM,
                enonce=f"Question {i}",
                competence_ciblee="Docker",
                choix=[
                    ChoixQCM(lettre="A", texte="Choix A"),
                    ChoixQCM(lettre="B", texte="Choix B"),
                    ChoixQCM(lettre="C", texte="Choix C"),
                    ChoixQCM(lettre="D", texte="Choix D"),
                ],
                points=2,
            )
            questions.append(q)

        exercice = ExercicePratique(
            enonce="Écrivez un Dockerfile.",
            competence_ciblee="Docker",
            criteres_correction=["FROM python", "EXPOSE 5000"],
            points=10,
        )
        test_public = TechTestPublic(
            test_id="test-mock-001",
            titre="Test technique — DevOps",
            job_title="DevOps",
            duree_minutes=45,
            questions=questions,
            exercice=exercice,
            instructions="Répondez aux questions.",
        )

        # Mock du générateur
        instance_mock = MagicMock()
        instance_mock.generate.return_value = (MagicMock(), test_public, "test-mock-001")
        MockGenerator.return_value = instance_mock

        response = self.client.post(
            "/api/v1/tests/generate",
            json={
                "job_title": "DevOps Junior",
                "job_description": "Nous cherchons un ingénieur DevOps junior.",
                "competences_manquantes": [{"skill": "Docker", "level": "intermédiaire"}],
                "candidate_id": "cand-anonyme-001",
            },
        )

        assert response.status_code == 201
        data = response.json()
        assert "questions" in data
        assert len(data["questions"]) == 5
        # Vérifier que les réponses correctes ne sont pas exposées
        for q in data["questions"]:
            assert q.get("reponse_correcte") is None

    @patch("app.v1.endpoints.tech_assessment.TechTestGenerator")
    def test_llm_indisponible_retourne_503(self, MockGenerator):
        """Si le LLM est indisponible, l'endpoint doit retourner 503."""
        from app.coreAgents.Agents.tech_assessment.generator import TechTestGenerationError

        instance_mock = MagicMock()
        instance_mock.generate.side_effect = TechTestGenerationError("LLM indisponible")
        MockGenerator.return_value = instance_mock

        response = self.client.post(
            "/api/v1/tests/generate",
            json={
                "job_title": "DevOps Junior",
                "job_description": "Description du poste DevOps Junior.",
                "competences_manquantes": [{"skill": "Docker", "level": "intermédiaire"}],
                "candidate_id": "cand-anonyme-001",
            },
        )

        assert response.status_code == 503
        assert "indisponible" in response.json()["detail"].lower()
