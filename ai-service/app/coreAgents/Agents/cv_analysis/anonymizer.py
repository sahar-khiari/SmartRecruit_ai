"""Étape 2 du pipeline : anonymisation du CV avant tout envoi à un LLM.

1. Regex du service commun (AnonymizationService, réutilisé sans modification) :
   emails, téléphones internationaux, URL, IP, identifiants et noms étiquetés.
2. Ajouts propres aux CV :
   - téléphones tunisiens sans indicatif (« 22 333 444 ») ;
   - en-tête du CV : la première ligne est presque toujours le nom, souvent en majuscules,
     un cas que le NER reconnaît mal ;
   - NER spaCy (fr_core_news_md, local) : personnes (PER) et lieux (LOC) non étiquetés.
Les termes techniques (Java, Spring...) ne sont jamais masqués : ce sont les compétences à extraire.
Aucune valeur d'origine n'est journalisée ni retournée.
"""

import logging
import re
from dataclasses import dataclass
from functools import lru_cache

import spacy
from spacy.language import Language

from app.coreAgents.Agents.cv_analysis.constants import CV_SECTION_TERMS, SPACY_MODEL, TECH_TERMS
from app.services.anonymization_service import AnonymizationService

logger = logging.getLogger(__name__)

NAME = "[NAME]"
LOCATION = "[LOCATION]"


@dataclass(frozen=True)
class CvAnonymizationResult:
    text: str
    replaced_count: int
    categories: tuple[str, ...]


@lru_cache
def load_nlp() -> Language:
    """Charge le modèle spaCy une seule fois (environ 1 s). Seuls le tokenizer et le NER sont utiles."""
    nlp = spacy.load(SPACY_MODEL, disable=["parser", "lemmatizer", "morphologizer", "attribute_ruler"])
    logger.info("Modèle spaCy chargé | model=%s", SPACY_MODEL)
    return nlp


class CvAnonymizer:
    _PHONE_TN = re.compile(r"(?<![\d+])[2-9]\d[ .\-]?\d{3}[ .\-]?\d{3}(?!\d)")
    _NAME_WORD = re.compile(r"^[^\W\d_][\w'’\-]*$")
    _REPEATED_NAME = re.compile(r"\[NAME\](?:[ \t]*[\-–—,]?[ \t]*\[NAME\])+")

    def __init__(self, nlp: Language | None = None, base: AnonymizationService | None = None) -> None:
        self._nlp = nlp
        self._base = base or AnonymizationService()

    def anonymize(self, text: str) -> CvAnonymizationResult:
        base = self._base.anonymize(text)
        counts = {category: 0 for category in base.categories}
        current = base.text

        current, phones = self._PHONE_TN.subn("[PHONE]", current)
        if phones:
            counts["PHONE"] = counts.get("PHONE", 0) + phones

        names = set(self._header_name(current))
        locations: set[str] = set()
        nlp = self._nlp or load_nlp()
        for ent in nlp(current).ents:
            if not self._is_maskable(ent.text):
                continue
            if ent.label_ == "PER":
                names.add(ent.text.strip())
            elif ent.label_ == "LOC":
                locations.add(ent.text.strip())

        current, masked_names = self._mask(current, self._name_variants(names), NAME)
        current, masked_locations = self._mask(current, locations - names, LOCATION)
        current = self._REPEATED_NAME.sub(NAME, current)
        if masked_names:
            counts["NAME"] = counts.get("NAME", 0) + masked_names
        if masked_locations:
            counts["LOCATION"] = counts.get("LOCATION", 0) + masked_locations

        return CvAnonymizationResult(
            text=current,
            replaced_count=base.replaced_count + phones + masked_names + masked_locations,
            categories=tuple(sorted(set(counts) | set(base.categories))),
        )

    def _header_name(self, text: str) -> list[str]:
        """La première ligne non vide, si elle ressemble à « Prénom Nom » (2 à 4 mots, sans chiffre)."""
        first_line = next((line.strip() for line in text.splitlines() if line.strip()), "")
        words = first_line.split()
        if (
            2 <= len(words) <= 4
            and len(first_line) <= 60
            and all(self._NAME_WORD.match(word) and word[0].isupper() for word in words)
            and self._is_maskable(first_line)
        ):
            return [first_line]
        return []

    def _name_variants(self, names: set[str]) -> set[str]:
        """Nom complet + chacun de ses mots, pour masquer aussi « Ahmed » seul plus loin dans le CV.
        Les mots isolés ne sont ajoutés que pour un vrai nom (2 à 4 mots tous en majuscule initiale)."""
        variants = set(names)
        for name in names:
            words = name.split()
            if 2 <= len(words) <= 4 and all(word[0].isupper() for word in words):
                variants.update(word for word in words if len(word) >= 3 and self._is_maskable(word))
        return variants

    @staticmethod
    def _is_maskable(value: str) -> bool:
        value = value.strip()
        if len(value) < 2 or "[" in value or any(char.isdigit() for char in value):
            return False
        lowered = value.lower()
        if lowered in CV_SECTION_TERMS:
            return False
        return lowered not in TECH_TERMS and not any(word in TECH_TERMS for word in lowered.split())

    @staticmethod
    def _mask(text: str, values: set[str], placeholder: str) -> tuple[str, int]:
        if not values:
            return text, 0
        # Les plus longs d'abord : « Ahmed Ben Salah » avant « Ahmed ».
        alternatives = "|".join(re.escape(value) for value in sorted(values, key=len, reverse=True))
        pattern = re.compile(rf"(?<!\w)(?:{alternatives})(?!\w)", re.IGNORECASE)
        return pattern.subn(placeholder, text)
