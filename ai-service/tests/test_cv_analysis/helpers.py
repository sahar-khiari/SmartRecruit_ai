"""Outils partagés des tests du Module 1 (CV fictifs, LLM simulé). Jamais de vrai CV."""

import json

from fpdf import FPDF

from app.coreAgents.Agents.cv_analysis.llm.base import LlmError, LlmResult

TEST_API_KEY = "test-internal-key"

VALID_EXTRACTION = {
    "headline": "Développeur Full Stack Java / Angular",
    "summary": "Le candidat est développeur full stack avec 3 ans d'expérience en Java et Angular.",
    "years_of_experience": 3,
    "skills": ["Java", "Spring Boot", "Angular", "Docker", "MySQL", "Git"],
    "soft_skills": ["Travail en équipe"],
    "languages": ["Français", "Anglais (B2)"],
}


class FakeLlmClient:
    """LLM simulé : renvoie, dans l'ordre, chaque réponse (texte JSON) ou lève chaque erreur de la liste."""

    def __init__(self, model: str, responses: list[str | dict | LlmError]) -> None:
        self.model = model
        self._responses = list(responses)
        self.calls: list[str] = []

    def generate_json(self, system: str, prompt: str, schema: dict) -> LlmResult:
        self.calls.append(prompt)
        response = self._responses.pop(0) if len(self._responses) > 1 else self._responses[0]
        if isinstance(response, LlmError):
            raise response
        text = json.dumps(response) if isinstance(response, dict) else response
        return LlmResult(text=text, model=self.model, latency_ms=12, input_tokens=500, output_tokens=120)


def build_pdf(lines: list[str], pages: int = 1) -> bytes:
    """Génère un CV PDF fictif (jamais de vrai CV dans les tests)."""
    pdf = FPDF()
    pdf.set_font("Helvetica", size=11)
    for _ in range(pages):
        pdf.add_page()
        for line in lines:
            pdf.multi_cell(0, 6, line, new_x="LMARGIN", new_y="NEXT")
    return bytes(pdf.output())


FICTIONAL_CV = [
    "Ahmed Ben Salah",
    "ahmed.bensalah@example.com | +216 22 333 444 | 98 765 432",
    "https://github.com/ahmed-bs",
    "Développeur Full Stack Java / Angular",
    "Expérience",
    "2021 - 2024 : Développeur chez Vermeg, Tunis. Ahmed a développé des API REST avec Spring Boot.",
    "Compétences : Java, Spring Boot, Angular, Docker, MySQL, Git",
    "Formation : Diplôme d'ingénieur, ESPRIT, Ariana",
]



# Conseils du Coach CV : la 2e reformulation invente un chiffre (« 30 % ») et doit être écartée.
VALID_COACH_ADVICE = {
    "strengths": ["Expérience récente sur Spring Boot", "Compétences techniques variées"],
    "improvements": [
        {"title": "Ajoutez une rubrique Formation", "detail": "Indiquez votre diplôme et votre école."},
        {"title": "Chiffrez vos résultats", "detail": "Précisez le nombre d'API ou d'utilisateurs."},
    ],
    "rewrites": [
        {"before": "a développé des API REST avec Spring Boot",
         "after": "Conception de [nombre] API REST Spring Boot utilisées en production",
         "reason": "Verbe d'action et résultat mesurable"},
        {"before": "a développé des API REST avec Spring Boot",
         "after": "Développement d'API REST ayant amélioré les performances de 30 %",
         "reason": "Chiffre inventé : doit être rejeté"},
    ],
}
