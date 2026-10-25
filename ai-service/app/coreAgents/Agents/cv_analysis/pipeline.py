"""Enchaîne les étapes du pipeline d'analyse de CV et mesure la durée de chacune.

1. Lecture du PDF (pdfplumber)          2. Anonymisation (regex + spaCy)
3. Extraction structurée (Gemini, secours Gemini Flash-Lite)
4. Niveau d'expérience (règle métier)
5. Normalisation des compétences (référentiel + rapidfuzz)
"""

import logging
import time
from dataclasses import dataclass, field

from app.coreAgents.Agents.cv_analysis.anonymizer import CvAnonymizer
from app.coreAgents.Agents.cv_analysis.extractor import CvExtractor, ExtractionOutcome
from app.coreAgents.Agents.cv_analysis.pdf_reader import read_pdf_text
from app.coreAgents.Agents.cv_analysis.profiler import ExperienceLevel, experience_level_from_years
from app.coreAgents.Agents.cv_analysis.skill_normalizer import (
    NormalizedSkill,
    SkillNormalizer,
    get_skill_normalizer,
)

logger = logging.getLogger(__name__)

PIPELINE_VERSION = "cv-pipeline-v3"


@dataclass(frozen=True)
class CvPipelineResult:
    anonymized_text: str
    page_count: int
    text_chars: int
    truncated: bool
    replaced_count: int
    categories: tuple[str, ...]
    extraction: ExtractionOutcome
    experience_level: ExperienceLevel | None
    skills: tuple[NormalizedSkill, ...]
    skills_referential_version: str
    durations_ms: dict[str, int] = field(default_factory=dict)


class CvAnalysisPipeline:

    def __init__(self, extractor: CvExtractor, anonymizer: CvAnonymizer | None = None,
                 normalizer: SkillNormalizer | None = None) -> None:
        self._extractor = extractor
        self._anonymizer = anonymizer or CvAnonymizer()
        self._normalizer = normalizer or get_skill_normalizer()

    def run(self, content: bytes) -> CvPipelineResult:
        durations: dict[str, int] = {}

        started = time.perf_counter()
        pdf = read_pdf_text(content)
        durations["pdf_read"] = _elapsed_ms(started)

        started = time.perf_counter()
        anonymized = self._anonymizer.anonymize(pdf.text)
        durations["anonymization"] = _elapsed_ms(started)

        # Seul le texte anonymisé quitte le serveur.
        started = time.perf_counter()
        extraction = self._extractor.extract(anonymized.text)
        durations["extraction"] = _elapsed_ms(started)

        level = experience_level_from_years(extraction.data.years_of_experience)

        started = time.perf_counter()
        skills = self._normalizer.normalize(extraction.data.skills)
        durations["normalization"] = _elapsed_ms(started)

        logger.info(
            "CV analysé | pages=%d chars=%d remplacements=%d model=%s secours=%s competences=%d durees_ms=%s",
            pdf.page_count, len(pdf.text), anonymized.replaced_count, extraction.model,
            extraction.fallback_used, len(skills), durations,
        )
        return CvPipelineResult(
            anonymized_text=anonymized.text,
            page_count=pdf.page_count,
            text_chars=len(pdf.text),
            truncated=pdf.truncated,
            replaced_count=anonymized.replaced_count,
            categories=anonymized.categories,
            extraction=extraction,
            experience_level=level,
            skills=tuple(skills),
            skills_referential_version=self._normalizer.version,
            durations_ms=durations,
        )


def _elapsed_ms(started: float) -> int:
    return round((time.perf_counter() - started) * 1000)
