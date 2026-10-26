"""Coach CV : score par règles + conseils rédigés par le LLM, avec garde-fou anti-invention.

Garde-fou sur les reformulations proposées par le LLM :
- « before » doit exister dans le CV (comparaison sans casse ni espaces multiples) ;
- « after » ne doit contenir aucun chiffre absent de « before » (sinon le LLM aurait inventé un résultat :
  il doit écrire « [nombre] ») ni aucun marqueur d'anonymisation.
Une reformulation qui échoue est écartée et comptée (rejected_rewrites).
"""

import json
import re
import time
from dataclasses import dataclass, field

from pydantic import ValidationError

from app.coreAgents.Agents.cv_analysis.anonymizer import CvAnonymizer
from app.coreAgents.Agents.cv_analysis.coach_rules import CoachCriterion, evaluate, total_score
from app.coreAgents.Agents.cv_analysis.llm.base import LlmClient, LlmOutputError
from app.coreAgents.Agents.cv_analysis.llm.runner import LlmRunOutcome, run_json
from app.coreAgents.Agents.cv_analysis.pdf_reader import read_pdf_text
from app.coreAgents.Agents.cv_analysis.prompts import (
    COACH_PROMPT_VERSION,
    COACH_RESPONSE_SCHEMA,
    COACH_SYSTEM_PROMPT,
    build_coach_prompt,
)
from app.v1.schemas.cv_analysis import CoachRewrite, CvCoachLlmAdvice

_SPACES = re.compile(r"\s+")
_DIGITS = re.compile(r"\d+")
_FORBIDDEN_MARKERS = re.compile(r"\[(?:NAME|EMAIL|PHONE|URL|LOCATION|IDENTIFIER|IP|SECRET|TOKEN)\]")


def _normalize(text: str) -> str:
    return _SPACES.sub(" ", text).strip().lower()


def is_grounded_rewrite(rewrite: CoachRewrite, cv_text: str) -> bool:
    before = _normalize(rewrite.before)
    after = rewrite.after.strip()
    if len(before) < 12 or not after or _normalize(after) == before:
        return False
    if before not in _normalize(cv_text):
        return False
    if _FORBIDDEN_MARKERS.search(after):
        return False
    return set(_DIGITS.findall(after)) <= set(_DIGITS.findall(rewrite.before))


@dataclass(frozen=True)
class CoachResult:
    score: int
    criteria: list[CoachCriterion]
    advice: CvCoachLlmAdvice
    rejected_rewrites: int
    llm: LlmRunOutcome
    durations_ms: dict[str, int] = field(default_factory=dict)


class CvCoach:

    def __init__(self, clients: list[LlmClient], max_retries: int = 1,
                 anonymizer: CvAnonymizer | None = None) -> None:
        self._clients = clients
        self._max_retries = max_retries
        self._anonymizer = anonymizer or CvAnonymizer()

    def run(self, content: bytes) -> CoachResult:
        durations: dict[str, int] = {}

        started = time.perf_counter()
        pdf = read_pdf_text(content)
        durations["pdf_read"] = _elapsed_ms(started)

        started = time.perf_counter()
        text = self._anonymizer.anonymize(pdf.text).text
        durations["anonymization"] = _elapsed_ms(started)

        started = time.perf_counter()
        criteria = evaluate(text)
        durations["rules"] = _elapsed_ms(started)

        summary = "\n".join(f"- {c.label} : {c.points}/{c.max_points}" for c in criteria)
        started = time.perf_counter()
        outcome = run_json(self._clients, self._max_retries, COACH_SYSTEM_PROMPT,
                           build_coach_prompt(text, summary), COACH_RESPONSE_SCHEMA,
                           self._parse, task="Coach CV")
        durations["advice"] = _elapsed_ms(started)

        grounded = [r for r in outcome.data.rewrites if is_grounded_rewrite(r, text)][:3]
        rejected = len(outcome.data.rewrites) - len(grounded)
        advice = outcome.data.model_copy(update={"rewrites": grounded})
        return CoachResult(total_score(criteria), criteria, advice, rejected, outcome, durations)

    @staticmethod
    def _parse(text: str, model: str) -> CvCoachLlmAdvice:
        try:
            return CvCoachLlmAdvice.model_validate(json.loads(text))
        except (json.JSONDecodeError, ValidationError) as exc:
            raise LlmOutputError(f"JSON invalide ({type(exc).__name__})", model) from exc


def _elapsed_ms(started: float) -> int:
    return round((time.perf_counter() - started) * 1000)


__all__ = ["COACH_PROMPT_VERSION", "CoachResult", "CvCoach", "is_grounded_rewrite"]
