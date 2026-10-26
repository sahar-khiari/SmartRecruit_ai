"""Coach CV : score par règles, garde-fou anti-invention, endpoint (LLM simulé)."""

from app.coreAgents.Agents.cv_analysis.coach import is_grounded_rewrite
from app.coreAgents.Agents.cv_analysis.coach_rules import evaluate, total_score
from app.coreAgents.Agents.cv_analysis.llm.base import LlmUnavailableError
from app.v1.schemas.cv_analysis import CoachRewrite

from .helpers import FICTIONAL_CV, TEST_API_KEY

COMPLETE_CV = """[NAME]
[EMAIL] | [PHONE]
Développeur Full Stack
Expérience
2021 - 2024 : Développeur chez [LOCATION]. Conception de 12 API REST utilisées par 3 applications,
temps de réponse réduit de 40 %. Java, Spring Boot, Hibernate, Angular, Docker, MySQL, Git, Kubernetes.
2019 - 2021 : Développeur junior. Maintenance d'applications PHP Laravel.
Formation
2019 : Diplôme d'ingénieur en génie logiciel.
Compétences
Java, Spring Boot, Angular, TypeScript, Docker, Kubernetes, MySQL, PostgreSQL, Git, Jenkins
Langues
Français, Anglais (B2)
""" + "Projet personnel : application de gestion de tâches en Angular et Spring Boot. " * 12


def test_complete_cv_gets_high_and_stable_score():
    first = evaluate(COMPLETE_CV)
    assert total_score(first) >= 90
    assert total_score(evaluate(COMPLETE_CV)) == total_score(first), "même CV = même score"


def test_poor_cv_gets_low_score_with_advice():
    criteria = evaluate("[NAME]\nDéveloppeur\nJ'aime programmer.")
    assert total_score(criteria) < 30
    missing = {c.id for c in criteria if not c.passed}
    assert {"experience", "education", "quantified"} <= missing
    assert all(c.advice for c in criteria if not c.passed)


def test_internship_duration_is_not_a_quantified_achievement():
    criteria = {c.id: c for c in evaluate("Stage de 4 mois en 2023. Stage de 2 mois en 2022.")}
    assert criteria["quantified"].points == 0


def test_rewrite_must_quote_the_cv():
    cv = "Expérience : a développé des API REST avec Spring Boot pour une banque."
    ok = CoachRewrite(before="a développé des API REST avec Spring Boot",
                      after="Conception de [nombre] API REST Spring Boot pour le secteur bancaire")
    assert is_grounded_rewrite(ok, cv)
    assert not is_grounded_rewrite(ok.model_copy(update={"before": "a dirigé une équipe de développeurs"}), cv)


def test_rewrite_cannot_invent_numbers_or_reveal_markers():
    cv = "a développé des API REST avec Spring Boot"
    invented = CoachRewrite(before=cv, after="Développement de 15 API REST Spring Boot")
    marker = CoachRewrite(before=cv, after="[NAME] a conçu des API REST Spring Boot")
    assert not is_grounded_rewrite(invented, cv)
    assert not is_grounded_rewrite(marker, cv)


def test_coach_endpoint_returns_score_advice_and_filters_invented_rewrite(client, make_pdf, coach_clients):
    response = client.post("/api/v1/cv/coach", headers={"X-AI-Service-Key": TEST_API_KEY},
                           files={"file": ("cv.pdf", make_pdf(FICTIONAL_CV), "application/pdf")})

    assert response.status_code == 200
    body = response.json()
    assert 0 <= body["score"] <= 100
    assert body["score"] == sum(c["points"] for c in body["criteria"])
    assert body["prompt_version"] == "cv-coach-v1"
    assert len(body["strengths"]) == 2
    assert len(body["rewrites"]) == 1 and "[nombre]" in body["rewrites"][0]["after"]
    assert body["rejected_rewrites"] == 1
    assert "Ahmed" not in coach_clients[0].calls[0], "le LLM ne reçoit que le CV anonymisé"


def test_coach_endpoint_returns_503_when_models_are_down(client, make_pdf, coach_clients):
    for fake in coach_clients:
        fake._responses = [LlmUnavailableError("503")]

    response = client.post("/api/v1/cv/coach", headers={"X-AI-Service-Key": TEST_API_KEY},
                           files={"file": ("cv.pdf", make_pdf(FICTIONAL_CV), "application/pdf")})

    assert response.status_code == 503
    assert response.json()["code"] == "LLM_UNAVAILABLE"
