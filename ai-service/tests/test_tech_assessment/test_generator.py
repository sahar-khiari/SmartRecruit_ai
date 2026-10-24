"""
Tests du générateur de tests techniques — Module 4.
Auteur : Aziz (module4-aziz)

On mocke le SLMClient pour tester la logique du générateur
sans appeler le vrai LLM (tests rapides, pas de dépendance réseau).

Ce qu'on teste :
  1. Le générateur appelle le LLM et valide la réponse correctement.
  2. Il lève TechTestGenerationError si le LLM est indisponible.
  3. Il lève TechTestGenerationError si la réponse JSON ne respecte pas le schéma.
  4. La version publique ne contient pas les réponses correctes.
  5. La persistance BDD est appelée (ou loguée en cas d'erreur).

Tests corrigés après génération IA :
  - Correction du mock : SLMClient.chat_json retourne un dict, pas une string.
  - Ajout de la vérification que reponse_correcte est None dans le test public.
"""

from unittest.mock import MagicMock, patch

import pytest

from app.coreAgents.Agents.tech_assessment.generator import (
    TechTestGenerationError,
    TechTestGenerator,
)
from app.v1.schemas.tech_assessment import (
    CompetenceManquante,
    GenerateTestRequest,
    NiveauCompetence,
)


# ---------------------------------------------------------------------------
# Fixture : réponse LLM valide (simule ce que chat_json retourne)
# ---------------------------------------------------------------------------

@pytest.fixture
def reponse_llm_valide() -> dict:
    """JSON valide conforme au schéma TechTest."""
    return {
        "titre": "Test technique — DevOps Junior",
        "job_title": "DevOps Junior",
        "duree_minutes": 45,
        "questions": [
            {
                "numero": 1,
                "type": "qcm",
                "enonce": "Qu'est-ce que Docker ?",
                "competence_ciblee": "Docker",
                "choix": [
                    {"lettre": "A", "texte": "Un OS"},
                    {"lettre": "B", "texte": "Un outil de conteneurisation"},
                    {"lettre": "C", "texte": "Un langage"},
                    {"lettre": "D", "texte": "Un service cloud"},
                ],
                "reponse_correcte": "B",
                "points": 2,
            },
            {
                "numero": 2,
                "type": "ouverte",
                "enonce": "Expliquez l'utilité de docker-compose.",
                "competence_ciblee": "Docker",
                "reponse_correcte": "Orchestration de plusieurs conteneurs.",
                "points": 4,
            },
            {
                "numero": 3,
                "type": "qcm",
                "enonce": "Quelle commande lance un conteneur ?",
                "competence_ciblee": "Docker",
                "choix": [
                    {"lettre": "A", "texte": "docker build"},
                    {"lettre": "B", "texte": "docker run"},
                    {"lettre": "C", "texte": "docker pull"},
                    {"lettre": "D", "texte": "docker push"},
                ],
                "reponse_correcte": "B",
                "points": 2,
            },
            {
                "numero": 4,
                "type": "ouverte",
                "enonce": "Qu'est-ce qu'un volume Docker ?",
                "competence_ciblee": "Docker",
                "reponse_correcte": "Mécanisme de persistance des données hors conteneur.",
                "points": 4,
            },
            {
                "numero": 5,
                "type": "qcm",
                "enonce": "Quel fichier décrit les services docker-compose ?",
                "competence_ciblee": "Docker",
                "choix": [
                    {"lettre": "A", "texte": "Makefile"},
                    {"lettre": "B", "texte": "docker-compose.yml"},
                    {"lettre": "C", "texte": "Dockerfile"},
                    {"lettre": "D", "texte": ".env"},
                ],
                "reponse_correcte": "B",
                "points": 2,
            },
        ],
        "exercice": {
            "enonce": "Écrivez un Dockerfile pour une app Python Flask.",
            "competence_ciblee": "Docker",
            "criteres_correction": [
                "Utilise FROM python:3.11-slim",
                "Installe les dépendances",
                "Expose le port 5000",
            ],
            "points": 10,
        },
        "instructions": "Répondez à chaque question. Ne partagez pas de données personnelles.",
    }


@pytest.fixture
def requete_valide() -> GenerateTestRequest:
    return GenerateTestRequest(
        job_title="DevOps Junior",
        job_description="Nous cherchons un ingénieur DevOps junior maîtrisant Docker et Kubernetes.",
        competences_manquantes=[
            CompetenceManquante(skill="Docker", level=NiveauCompetence.INTERMEDIAIRE),
            CompetenceManquante(skill="Kubernetes", level=NiveauCompetence.DEBUTANT),
        ],
        candidate_id="cand-anonyme-001",
    )


# ---------------------------------------------------------------------------
# Helper : crée un générateur avec un SLMClient mocké et sans BDD réelle
# ---------------------------------------------------------------------------

def _creer_generateur_mocke(reponse_llm: dict) -> TechTestGenerator:
    """
    Crée un TechTestGenerator avec :
      - un SLMClient dont chat_json retourne la réponse fournie
      - une session BDD factice (pour éviter les accès SQLite pendant les tests)
    """
    llm_mock = MagicMock()
    llm_mock.chat_json.return_value = reponse_llm

    # Session BDD factice — on ne teste pas la persistance ici
    session_mock = MagicMock()
    session_factory_mock = MagicMock(return_value=session_mock)

    generateur = TechTestGenerator(
        session_factory=session_factory_mock,
        llm_client=llm_mock,
    )
    return generateur


# ---------------------------------------------------------------------------
# Tests principaux
# ---------------------------------------------------------------------------

class TestTechTestGenerator:

    def test_generation_reussie(self, requete_valide, reponse_llm_valide):
        """Le générateur retourne un test valide et une version publique."""
        generateur = _creer_generateur_mocke(reponse_llm_valide)

        test_complet, test_public, test_id = generateur.generate(requete_valide)

        # Vérifications sur le test complet
        assert test_complet.titre == "Test technique — DevOps Junior"
        assert len(test_complet.questions) == 5

        # Vérifications sur la version publique
        assert test_public.test_id == test_id
        assert len(test_public.questions) == 5

        # IMPORTANT : les réponses correctes ne doivent PAS être dans la version publique
        for q in test_public.questions:
            assert q.reponse_correcte is None, (
                f"La réponse de la question {q.numero} est exposée dans la version publique !"
            )

    def test_llm_indisponible_leve_erreur_metier(self, requete_valide):
        """Si le LLM est indisponible, on lève TechTestGenerationError (pas une exception technique brute)."""
        from app.coreAgents.llm_client import LLMError

        llm_mock = MagicMock()
        llm_mock.chat_json.side_effect = LLMError("Groq quota dépassé")

        generateur = TechTestGenerator(
            session_factory=MagicMock(return_value=MagicMock()),
            llm_client=llm_mock,
        )

        with pytest.raises(TechTestGenerationError, match="temporairement indisponible"):
            generateur.generate(requete_valide)

    def test_json_invalide_leve_erreur_metier(self, requete_valide):
        """Si le LLM renvoie un JSON malformé, on lève TechTestGenerationError."""
        llm_mock = MagicMock()
        llm_mock.chat_json.side_effect = ValueError("JSON non valide")

        generateur = TechTestGenerator(
            session_factory=MagicMock(return_value=MagicMock()),
            llm_client=llm_mock,
        )

        with pytest.raises(TechTestGenerationError, match="JSON valide"):
            generateur.generate(requete_valide)

    def test_schema_non_conforme_leve_erreur_metier(self, requete_valide):
        """Si le JSON est valide mais ne respecte pas le schéma TechTest, erreur propre."""
        # On retourne un JSON incomplet (pas de 'questions')
        reponse_invalide = {"titre": "Test sans questions"}

        generateur = _creer_generateur_mocke(reponse_invalide)

        with pytest.raises(TechTestGenerationError, match="schema attendu"):
            generateur.generate(requete_valide)

    def test_prompt_v1_utilise(self, requete_valide, reponse_llm_valide):
        """Vérifier que le sélecteur de version de prompt fonctionne."""
        generateur = _creer_generateur_mocke(reponse_llm_valide)
        generateur.generate(requete_valide, prompt_version="v1")
        # Si on arrive ici sans exception, v1 est bien reconnu
        assert True

    def test_prompt_v3_utilise(self, requete_valide, reponse_llm_valide):
        """Version v3 (chain-of-thought) doit être reconnue."""
        generateur = _creer_generateur_mocke(reponse_llm_valide)
        generateur.generate(requete_valide, prompt_version="v3")
        assert True

    def test_uuid_test_id_unique(self, requete_valide, reponse_llm_valide):
        """Chaque test généré doit avoir un ID unique."""
        generateur = _creer_generateur_mocke(reponse_llm_valide)
        _, _, id1 = generateur.generate(requete_valide)
        _, _, id2 = generateur.generate(requete_valide)
        assert id1 != id2

    def test_erreur_persistance_ne_bloque_pas(self, requete_valide, reponse_llm_valide):
        """Une erreur BDD ne doit pas empêcher la génération du test."""
        llm_mock = MagicMock()
        llm_mock.chat_json.return_value = reponse_llm_valide

        # Simuler une erreur de BDD
        session_mock = MagicMock()
        session_mock.add.side_effect = Exception("Erreur BDD simulée")
        session_factory_mock = MagicMock(return_value=session_mock)

        generateur = TechTestGenerator(
            session_factory=session_factory_mock,
            llm_client=llm_mock,
        )

        # La génération doit réussir malgré l'erreur BDD
        test_complet, test_public, test_id = generateur.generate(requete_valide)
        assert test_complet is not None
        assert test_id is not None
