"""Étape 5 du pipeline : normalisation des compétences extraites (pas d'IA).

« ReactJS », « React.js » et « react » deviennent « React » : sans nom unique, le matching
avec les offres comparerait des chaînes différentes pour une même compétence.

Règles, dans l'ordre :
1. EXACT   : nom officiel ou variante connue du référentiel (casse, espaces, tirets et points ignorés) ;
2. VERSION : même chose sans le numéro de version final (« Java 17 », « Python 3.11 ») ;
3. FUZZY   : faute de frappe, par similarité rapidfuzz (seuil élevé, mots de 5 caractères minimum
             pour ne pas confondre « Go » et « Git ») ;
4. UNKNOWN : absente du référentiel. Elle est conservée telle quelle (compétence rare mais réelle).
"""

import json
import re
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Literal

from rapidfuzz import fuzz, process

REFERENTIAL_PATH = Path(__file__).parent / "data" / "skills_referential.json"
FUZZY_MIN_SCORE = 88
FUZZY_MIN_LENGTH = 5

MatchMethod = Literal["EXACT", "VERSION", "FUZZY", "UNKNOWN"]

_IGNORED_CHARS = re.compile(r"[\s\-_./]+")
_TRAILING_VERSION = re.compile(r"\s*v?\d+(?:\.\d+)*\+?$", re.IGNORECASE)


@dataclass(frozen=True)
class ReferenceSkill:
    name: str
    category: str


@dataclass(frozen=True)
class NormalizedSkill:
    original: str
    name: str
    category: str | None
    method: MatchMethod
    score: int


def _key(value: str) -> str:
    """Clé de comparaison : « Spring-Boot », « spring boot » et « SpringBoot » donnent « springboot ».
    « # » et « + » sont conservés : C, C# et C++ restent distincts."""
    return _IGNORED_CHARS.sub("", value.lower())


class SkillNormalizer:

    def __init__(self, referential: dict) -> None:
        self.version: str = referential["version"]
        self._by_key: dict[str, ReferenceSkill] = {}
        for entry in referential["skills"]:
            skill = ReferenceSkill(entry["name"], entry["category"])
            for variant in (entry["name"], *entry.get("aliases", [])):
                key = _key(variant)
                existing = self._by_key.get(key)
                if existing is not None and existing != skill:
                    raise ValueError(f"Variante en double dans le référentiel : « {variant} »")
                self._by_key[key] = skill
        self._fuzzy_keys = [key for key in self._by_key if len(key) >= FUZZY_MIN_LENGTH]

    @classmethod
    def from_file(cls, path: Path = REFERENTIAL_PATH) -> "SkillNormalizer":
        return cls(json.loads(path.read_text(encoding="utf-8")))

    def normalize(self, skills: list[str]) -> list[NormalizedSkill]:
        """Normalise et dédoublonne en gardant l'ordre du CV (« React » et « ReactJS » ne comptent qu'une fois)."""
        results: list[NormalizedSkill] = []
        seen: set[str] = set()
        for original in skills:
            normalized = self.normalize_one(original)
            if normalized is None or normalized.name.lower() in seen:
                continue
            seen.add(normalized.name.lower())
            results.append(normalized)
        return results

    def normalize_one(self, value: str) -> NormalizedSkill | None:
        original = " ".join(value.split())
        key = _key(original)
        if not key:
            return None

        skill = self._by_key.get(key)
        if skill:
            return NormalizedSkill(original, skill.name, skill.category, "EXACT", 100)

        without_version = _key(_TRAILING_VERSION.sub("", original))
        if without_version != key and without_version in self._by_key:
            skill = self._by_key[without_version]
            return NormalizedSkill(original, skill.name, skill.category, "VERSION", 100)

        if len(key) >= FUZZY_MIN_LENGTH:
            match = process.extractOne(key, self._fuzzy_keys, scorer=fuzz.ratio, score_cutoff=FUZZY_MIN_SCORE)
            if match:
                skill = self._by_key[match[0]]
                return NormalizedSkill(original, skill.name, skill.category, "FUZZY", round(match[1]))

        return NormalizedSkill(original, original, None, "UNKNOWN", 0)


@lru_cache
def get_skill_normalizer() -> SkillNormalizer:
    return SkillNormalizer.from_file()
