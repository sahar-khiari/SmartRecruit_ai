"""Coach CV - score /100 par règles déterministes (pas d'IA).

Le score n'est pas demandé au LLM : un même CV obtient toujours le même score, et chaque point
s'explique par un critère vérifiable. Le LLM rédige seulement les conseils (coach.py).
Les règles s'appliquent au texte anonymisé : les marqueurs [EMAIL] / [PHONE] prouvent la présence
des coordonnées sans les lire.
"""

import re
from dataclasses import dataclass

from app.coreAgents.Agents.cv_analysis.skill_normalizer import SkillNormalizer, get_skill_normalizer


@dataclass(frozen=True)
class CoachCriterion:
    id: str
    label: str
    points: int
    max_points: int
    advice: str | None  # conseil affiché si le critère n'est pas (entièrement) rempli

    @property
    def passed(self) -> bool:
        return self.points == self.max_points


_SECTION = {
    "experience": re.compile(r"(?im)^\W*(exp[ée]riences?(\s+professionnelles?)?|parcours\s+professionnel|work\s+experience|employment)\b"),
    "education": re.compile(r"(?im)^\W*(formations?|[ée]ducation|dipl[ôo]mes?|cursus|education)\b"),
    "skills": re.compile(r"(?im)^\W*(comp[ée]tences(\s+techniques)?|skills|technologies|outils)\b"),
    "languages": re.compile(r"(?im)^\W*(langues|languages)\b"),
}
_YEAR = re.compile(r"(?<!\d)(?:19[89]\d|20[0-4]\d)(?!\d)")
# « +30 % », « 12 API », « 3 applications », « 2 000 utilisateurs »...
# Pas les durées : « stage de 4 mois » n'est pas un résultat.
_QUANTIFIED = re.compile(
    r"(?i)(\+?\d[\d\s.,]*\s?%|\b\d[\d\s.,]*\s?(k€|€|utilisateurs?|clients?|projets?|applications?|"
    r"api|microservices?|personnes?|développeurs?|requêtes|transactions|fois)\b)"
)
_WORD = re.compile(r"[\w#+.]+(?:[ \-][\w#+.]+)?", re.UNICODE)


def _skill_mentions(text: str, normalizer: SkillNormalizer) -> int:
    """Compétences distinctes du référentiel citées dans le CV (correspondance exacte seulement)."""
    found: set[str] = set()
    for match in _WORD.finditer(text):
        for candidate in (match.group(0), match.group(0).split(" ")[0].split("-")[0]):
            skill = normalizer.normalize_one(candidate)
            if skill and skill.method in ("EXACT", "VERSION"):
                found.add(skill.name)
    return len(found)


def evaluate(anonymized_text: str, normalizer: SkillNormalizer | None = None) -> list[CoachCriterion]:
    normalizer = normalizer or get_skill_normalizer()
    text = anonymized_text
    criteria: list[CoachCriterion] = []

    def add(id_: str, label: str, points: int, max_points: int, advice: str) -> None:
        criteria.append(CoachCriterion(id_, label, points, max_points, None if points == max_points else advice))

    contact = int("[EMAIL]" in text) + int("[PHONE]" in text)
    add("contact", "Coordonnées (email et téléphone)", 5 * contact, 10,
        "Indiquez un email et un numéro de téléphone en haut du CV.")

    add("experience", "Rubrique Expérience", 15 if _SECTION["experience"].search(text) else 0, 15,
        "Ajoutez une rubrique « Expérience » avec vos postes, stages et projets.")
    add("education", "Rubrique Formation", 10 if _SECTION["education"].search(text) else 0, 10,
        "Ajoutez une rubrique « Formation » (diplôme, école, année).")
    add("skills_section", "Rubrique Compétences", 10 if _SECTION["skills"].search(text) else 0, 10,
        "Regroupez vos compétences techniques dans une rubrique dédiée.")
    add("languages", "Rubrique Langues", 5 if _SECTION["languages"].search(text) else 0, 5,
        "Précisez les langues parlées et votre niveau (ex. Anglais B2).")

    years = len(set(_YEAR.findall(text)))
    add("dates", "Expériences datées", 10 if years >= 2 else 5 if years == 1 else 0, 10,
        "Datez chaque expérience (mois et année de début et de fin).")

    quantified = len(_QUANTIFIED.findall(text))
    add("quantified", "Réalisations chiffrées", 15 if quantified >= 2 else 8 if quantified == 1 else 0, 15,
        "Chiffrez vos réalisations : nombre d'utilisateurs, de projets, gain de temps ou de performance.")

    skills = _skill_mentions(text, normalizer)
    add("skills_count", f"Compétences techniques citées ({skills})", 15 if skills >= 8 else 8 if skills >= 4 else 0,
        15, "Citez davantage de technologies précises (langages, frameworks, outils) en lien avec vos projets.")

    length = len(text)
    add("length", "Longueur adaptée", 10 if 1200 <= length <= 6000 else 5 if 600 <= length <= 10000 else 0, 10,
        "Visez un CV d'une à deux pages : développez vos expériences si le CV est trop court, "
        "ou résumez s'il est trop long.")

    return criteria


def total_score(criteria: list[CoachCriterion]) -> int:
    return sum(c.points for c in criteria)
