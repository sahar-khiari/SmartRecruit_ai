"""
Tests des schémas Pydantic — Module 4.
Auteur : Aziz (module4-aziz)

Ce fichier teste que les schémas acceptent les données valides
et rejettent les données invalides (règle prof §2 : toute sortie IA validée).

Ces tests ont été générés avec l'aide du LLM puis relus et corrigés :
- Correction : ajout du test de détection d'email dans job_description
- Correction : le validateur QCM vérifiait len != 4 mais Pydantic le traite
  avant le validator de champ ; test adapté pour s'assurer que l'exception est bien levée.
"""

import pytest
from pydantic import ValidationError

from app.v1.schemas.tech_assessment import (
    ChoixQCM,
    CompetenceManquante,
    EvaluationResult,
    ExercicePratique,
    GenerateTestRequest,
    NiveauCompetence,
    Question,
    RoadmapRequest,
    RoadmapResult,
    SemaineRoadmap,
    SubmitTestRequest,
    TechTest,
    TechTestPublic,
    TypeQuestion,
)


# ---------------------------------------------------------------------------
# Fixtures : données de test valides réutilisables
# ---------------------------------------------------------------------------

@pytest.fixture
def competence_valide() -> CompetenceManquante:
    return CompetenceManquante(skill="Docker", level=NiveauCompetence.INTERMEDIAIRE)


@pytest.fixture
def question_qcm_valide() -> Question:
    return Question(
        numero=1,
        type=TypeQuestion.QCM,
        enonce="Qu'est-ce qu'un conteneur Docker ?",
        competence_ciblee="Docker",
        choix=[
            ChoixQCM(lettre="A", texte="Un système d'exploitation"),
            ChoixQCM(lettre="B", texte="Un environnement d'exécution isolé"),
            ChoixQCM(lettre="C", texte="Un langage de programmation"),
            ChoixQCM(lettre="D", texte="Un service cloud"),
        ],
        reponse_correcte="B",
        points=2,
    )


@pytest.fixture
def question_ouverte_valide() -> Question:
    return Question(
        numero=2,
        type=TypeQuestion.OUVERTE,
        enonce="Expliquez la différence entre une image et un conteneur Docker.",
        competence_ciblee="Docker",
        reponse_correcte="L'image est un modèle immuable ; le conteneur est une instance en cours d'exécution.",
        points=4,
    )


@pytest.fixture
def exercice_valide() -> ExercicePratique:
    return ExercicePratique(
        enonce="Écrivez un Dockerfile pour une application Python Flask.",
        competence_ciblee="Docker",
        criteres_correction=[
            "Utilise FROM python:3.11-slim",
            "Copie les fichiers sources",
            "Installe les dépendances avec pip",
            "Expose le port 5000",
        ],
        points=10,
    )


@pytest.fixture
def tech_test_valide(
    question_qcm_valide, question_ouverte_valide, exercice_valide
) -> TechTest:
    """Construit un TechTest complet valide avec 5 questions."""
    questions = [
        question_qcm_valide,
        question_ouverte_valide,
        Question(
            numero=3, type=TypeQuestion.QCM,
            enonce="Quelle commande lance un conteneur ?",
            competence_ciblee="Docker",
            choix=[
                ChoixQCM(lettre="A", texte="docker build"),
                ChoixQCM(lettre="B", texte="docker run"),
                ChoixQCM(lettre="C", texte="docker pull"),
                ChoixQCM(lettre="D", texte="docker push"),
            ],
            reponse_correcte="B",
            points=2,
        ),
        Question(
            numero=4, type=TypeQuestion.OUVERTE,
            enonce="Qu'est-ce que docker-compose ?",
            competence_ciblee="Docker",
            reponse_correcte="Outil pour définir et lancer des applications multi-conteneurs.",
            points=4,
        ),
        Question(
            numero=5, type=TypeQuestion.QCM,
            enonce="Quel fichier décrit les services dans docker-compose ?",
            competence_ciblee="Docker",
            choix=[
                ChoixQCM(lettre="A", texte="Makefile"),
                ChoixQCM(lettre="B", texte="docker-compose.yml"),
                ChoixQCM(lettre="C", texte="Dockerfile"),
                ChoixQCM(lettre="D", texte=".env"),
            ],
            reponse_correcte="B",
            points=2,
        ),
    ]
    return TechTest(
        titre="Test technique — Développeur DevOps Junior",
        job_title="Développeur DevOps Junior",
        duree_minutes=45,
        questions=questions,
        exercice=exercice_valide,
    )


# ---------------------------------------------------------------------------
# Tests : CompetenceManquante
# ---------------------------------------------------------------------------

class TestCompetenceManquante:
    def test_valide(self, competence_valide):
        assert competence_valide.skill == "Docker"
        assert competence_valide.level == NiveauCompetence.INTERMEDIAIRE

    def test_niveau_par_defaut(self):
        c = CompetenceManquante(skill="Python")
        assert c.level == NiveauCompetence.INTERMEDIAIRE

    def test_skill_vide_rejete(self):
        with pytest.raises(ValidationError):
            CompetenceManquante(skill="")

    def test_skill_trop_long_rejete(self):
        with pytest.raises(ValidationError):
            CompetenceManquante(skill="x" * 101)

    def test_niveau_invalide_rejete(self):
        with pytest.raises(ValidationError):
            CompetenceManquante(skill="Python", level="expert")  # type: ignore


# ---------------------------------------------------------------------------
# Tests : GenerateTestRequest
# ---------------------------------------------------------------------------

class TestGenerateTestRequest:
    def test_valide(self, competence_valide):
        req = GenerateTestRequest(
            job_title="DevOps Engineer",
            job_description="Nous recherchons un ingénieur DevOps.",
            competences_manquantes=[competence_valide],
            candidate_id="cand-001",
        )
        assert req.job_title == "DevOps Engineer"

    def test_email_dans_description_rejete(self, competence_valide):
        """Règle prof §1 : pas de données personnelles envoyées au LLM."""
        with pytest.raises(ValidationError, match="adresse e-mail"):
            GenerateTestRequest(
                job_title="DevOps Engineer",
                job_description="Contact: jean.dupont@gmail.com pour postuler.",
                competences_manquantes=[competence_valide],
                candidate_id="cand-001",
            )

    def test_description_trop_courte_rejetee(self, competence_valide):
        with pytest.raises(ValidationError):
            GenerateTestRequest(
                job_title="DevOps Engineer",
                job_description="Court",
                competences_manquantes=[competence_valide],
                candidate_id="cand-001",
            )

    def test_competences_vides_rejetees(self):
        with pytest.raises(ValidationError):
            GenerateTestRequest(
                job_title="DevOps",
                job_description="Description correcte du poste DevOps.",
                competences_manquantes=[],
                candidate_id="cand-001",
            )


# ---------------------------------------------------------------------------
# Tests : Question
# ---------------------------------------------------------------------------

class TestQuestion:
    def test_qcm_avec_4_choix_valide(self, question_qcm_valide):
        assert question_qcm_valide.type == TypeQuestion.QCM
        assert len(question_qcm_valide.choix) == 4

    def test_qcm_avec_3_choix_rejete(self):
        with pytest.raises(ValidationError, match="exactement 4 choix"):
            Question(
                numero=1,
                type=TypeQuestion.QCM,
                enonce="Question test",
                competence_ciblee="Python",
                choix=[
                    ChoixQCM(lettre="A", texte="Choix 1"),
                    ChoixQCM(lettre="B", texte="Choix 2"),
                    ChoixQCM(lettre="C", texte="Choix 3"),
                ],
                points=2,
            )

    def test_question_ouverte_sans_choix_valide(self, question_ouverte_valide):
        assert question_ouverte_valide.choix is None

    def test_lettre_choix_invalide_rejetee(self):
        with pytest.raises(ValidationError):
            ChoixQCM(lettre="E", texte="Choix invalide")

    def test_points_hors_limites_rejetes(self):
        with pytest.raises(ValidationError):
            Question(
                numero=1, type=TypeQuestion.OUVERTE,
                enonce="Question",
                competence_ciblee="Python",
                points=15,  # max = 10
            )


# ---------------------------------------------------------------------------
# Tests : TechTest (test complet)
# ---------------------------------------------------------------------------

class TestTechTest:
    def test_test_valide(self, tech_test_valide):
        assert len(tech_test_valide.questions) == 5
        assert tech_test_valide.duree_minutes == 45

    def test_moins_de_5_questions_rejete(self, question_qcm_valide, exercice_valide):
        # Pydantic v2 lève 'too_short' via min_length=5 sur le Field AVANT notre
        # field_validator personnalisé. On vérifie juste qu'une ValidationError est levée.
        # ⚠️  Correction après génération IA : le match "exactement 5 questions" ne correspondait
        # pas au message réel de Pydantic v2 ("List should have at least 5 items...").
        with pytest.raises(ValidationError):
            TechTest(
                titre="Test",
                job_title="DevOps",
                questions=[question_qcm_valide, question_qcm_valide],  # seulement 2 → invalide
                exercice=exercice_valide,
            )

    def test_duree_hors_limites_rejetee(self, tech_test_valide, exercice_valide):
        with pytest.raises(ValidationError):
            TechTest(
                titre="Test",
                job_title="DevOps",
                duree_minutes=200,  # max = 120
                questions=tech_test_valide.questions,
                exercice=exercice_valide,
            )


# ---------------------------------------------------------------------------
# Tests : TechTestPublic (masquage des réponses correctes)
# ---------------------------------------------------------------------------

class TestTechTestPublic:
    def test_reponses_masquees(self, tech_test_valide):
        """Les réponses correctes ne doivent JAMAIS être envoyées au front."""
        public = TechTestPublic.depuis_test(test_id="test-123", test=tech_test_valide)
        for question in public.questions:
            assert question.reponse_correcte is None, (
                f"La réponse de la question {question.numero} est exposée !"
            )

    def test_contenu_conserve(self, tech_test_valide):
        public = TechTestPublic.depuis_test(test_id="test-123", test=tech_test_valide)
        assert public.test_id == "test-123"
        assert public.titre == tech_test_valide.titre
        assert len(public.questions) == 5


# ---------------------------------------------------------------------------
# Tests : EvaluationResult
# ---------------------------------------------------------------------------

class TestEvaluationResult:
    def test_valide(self):
        ev = EvaluationResult(
            score=75,
            score_questions=80,
            score_exercice=60,
            points_forts=["Bonne maîtrise des conteneurs"],
            points_faibles=["Docker Compose peu maîtrisé"],
            feedback="Bon niveau général, à améliorer sur docker-compose.",
            recommandation="PASSER",
        )
        assert ev.score == 75
        assert ev.recommandation == "PASSER"

    def test_recommandation_invalide_rejetee(self):
        with pytest.raises(ValidationError, match="recommandation"):
            EvaluationResult(
                score=75,
                score_questions=80,
                score_exercice=60,
                points_forts=["Bonne maîtrise"],
                points_faibles=[],
                feedback="Bon niveau.",
                recommandation="EXCELLENT",  # invalide
            )

    def test_recommandation_normalise_en_majuscules(self):
        ev = EvaluationResult(
            score=45,
            score_questions=50,
            score_exercice=30,
            points_forts=["Effort"],
            points_faibles=["Lacunes importantes"],
            feedback="Des efforts à fournir.",
            recommandation="approfondir",  # minuscules → doit être normalisé
        )
        assert ev.recommandation == "APPROFONDIR"

    def test_score_hors_limites_rejete(self):
        with pytest.raises(ValidationError):
            EvaluationResult(
                score=150,  # max = 100
                score_questions=80,
                score_exercice=60,
                points_forts=["X"],
                points_faibles=[],
                feedback="Feedback.",
                recommandation="PASSER",
            )


# ---------------------------------------------------------------------------
# Tests : RoadmapResult
# ---------------------------------------------------------------------------

class TestRoadmapResult:
    def test_valide(self):
        plan = [
            SemaineRoadmap(
                semaine=1,
                objectif="Maîtriser les bases de Docker",
                competences=["Docker"],
                ressources=["docs.docker.com", "Play with Docker"],
                mini_projet="Containeriser une application Flask",
            )
        ]
        roadmap = RoadmapResult(
            titre="Plan d'apprentissage DevOps",
            duree_semaines=8,
            plan=plan,
            conseil_final="Pratiquez chaque jour sur des projets réels.",
        )
        assert roadmap.duree_semaines == 8
        assert len(roadmap.plan) == 1
