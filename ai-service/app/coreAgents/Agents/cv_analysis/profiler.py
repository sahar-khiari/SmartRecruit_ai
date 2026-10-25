"""Niveau d'expérience : règle métier déterministe, pas une décision du LLM.

Même règle que l'enum Java ExperienceLevel.fromYears : 0-2 ans JUNIOR, 3-5 INTERMEDIATE, 6+ SENIOR.
Le LLM fournit seulement le nombre d'années ; le niveau est toujours recalculé ici,
pour être identique à celui que Spring calculerait et explicable en soutenance.
"""

from typing import Literal

ExperienceLevel = Literal["JUNIOR", "INTERMEDIATE", "SENIOR"]


def experience_level_from_years(years: int | None) -> ExperienceLevel | None:
    if years is None:
        return None
    if years <= 2:
        return "JUNIOR"
    return "INTERMEDIATE" if years <= 5 else "SENIOR"
