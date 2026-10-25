import re
from dataclasses import dataclass
from typing import Callable

from app.services.text_cleaning_service import TextCleaningService


@dataclass(frozen=True)
class AnonymizationResult:
    text: str
    replaced_count: int
    categories: tuple[str, ...]


class AnonymizationService:
    """Anonymisation heuristique avant indexation vectorielle.

    Limites explicites :
    - les règles couvrent les formats évidents (email, téléphone, URL, IP,
      NIR français, identifiants étiquetés, noms explicitement étiquetés) ;
    - elles ne remplacent pas un moteur de détection d'entités nommées ;
    - un nom non étiqueté, une adresse postale ou un identifiant au format
      inhabituel peuvent ne pas être détectés ;
    - aucune valeur d'origine n'est journalisée ni retournée.

    Cette anonymisation réduit le risque, mais ne garantit pas l'absence totale
    de donnée personnelle dans l'index.
    """

    _URL = re.compile(r"(?i)\b(?:https?://|www\.)[^\s<>\"']+")
    _EMAIL = re.compile(r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b")
    _IPV4 = re.compile(r"(?<!\d)(?:\d{1,3}\.){3}\d{1,3}(?!\d)")
    _NIR = re.compile(
        r"(?<!\d)[12](?:[\s.\-]?\d{2}){3}[\s.\-]?\d{3}[\s.\-]?\d{3}[\s.\-]?\d{2}(?!\d)"
    )
    _PHONE_FR = re.compile(
        r"(?<!\d)(?:\+33|0033|0)[\s.\-]?[1-9](?:[\s.\-]?\d{2}){4}(?!\d)"
    )
    _PHONE_INTERNATIONAL = re.compile(
        r"(?<![\d+])\+\d{1,3}(?:[\s.\-]?\d{2,4}){2,5}(?!\d)"
    )
    _LABELED_IDENTIFIER = re.compile(
        r"(?iu)\b(?:NIR|CIN|CNI|passeport|passport|permis|"
        r"s[ée]curit[ée]\s+sociale|identifiant\s+national)\b"
        r"[ \t]*[:#][ \t]*[A-Z0-9][A-Z0-9 \t\-]{4,24}\b"
    )
    _LABELED_SECRET = re.compile(
        r"(?iu)\b(?:mot\s+de\s+passe|password|passwd|pwd|token|jeton|"
        r"api[_\s-]?key|cl[ée]\s+(?:api|interne|secr[èe]te)|secret|jwt)\b"
        r"[ \t]*(?:(?::|=)[ \t]*[^\s,;]{4,}|"
        r"(?:\best\b|\bis\b)[ \t]*(?=[^\s,;]*(?:\d|[^\w\s]))[^\s,;]{4,})"
    )
    _JWT = re.compile(
        r"(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\."
        r"[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])"
    )
    _LABELED_NAME = re.compile(
        r"(?iu)\b(Nom\s+complet|Nom|Pr[ée]nom|Candidat(?:e)?|"
        r"Madame|Monsieur|Mme|Mlle|M\.)"
        r"[ \t]*:[ \t]*([^\W\d_][\w'’.\-]*(?:[ \t]+[^\W\d_][\w'’.\-]*){0,3})"
    )
    _TITLED_NAME = re.compile(
        r"(?iu)\b(Madame|Monsieur|Mme|Mlle|M\.)[ \t]+"
        r"([^\W\d_][\w'’.\-]*(?:[ \t]+[^\W\d_][\w'’.\-]*){0,2})"
    )

    def __init__(self, cleaning_service: TextCleaningService | None = None) -> None:
        self._cleaning = cleaning_service or TextCleaningService()

    def anonymize(self, text: str | None) -> AnonymizationResult:
        if text is None:
            raise ValueError("Le texte à anonymiser est obligatoire")

        current = text
        counts: dict[str, int] = {}

        current = self._replace(current, self._URL, "[URL]", "URL", counts)
        current = self._replace(current, self._EMAIL, "[EMAIL]", "EMAIL", counts)
        current = self._replace(
            current,
            self._IPV4,
            "[IP]",
            "IP",
            counts,
            validator=self._is_valid_ipv4,
        )
        current = self._replace(current, self._NIR, "[IDENTIFIER]", "IDENTIFIER", counts)
        current = self._replace(
            current, self._PHONE_FR, "[PHONE]", "PHONE", counts
        )
        current = self._replace(
            current, self._PHONE_INTERNATIONAL, "[PHONE]", "PHONE", counts
        )
        current = self._replace(current, self._JWT, "[TOKEN]", "SECRET", counts)
        current = self._replace(
            current, self._LABELED_SECRET, "[SECRET]", "SECRET", counts
        )
        current = self._replace(
            current, self._LABELED_IDENTIFIER, "[IDENTIFIER]", "IDENTIFIER", counts
        )
        current = self._replace(
            current,
            self._LABELED_NAME,
            None,
            "NAME",
            counts,
            formatter=lambda match: f"{match.group(1)} : [NAME]",
        )
        current = self._replace(
            current,
            self._TITLED_NAME,
            None,
            "NAME",
            counts,
            formatter=lambda match: f"{match.group(1)} [NAME]",
        )

        # Le nettoyage final reste idempotent et préserve le sens.
        try:
            cleaned = self._cleaning.clean(current)
        except ValueError:
            cleaned = current.strip()

        return AnonymizationResult(
            text=cleaned,
            replaced_count=sum(counts.values()),
            categories=tuple(sorted(counts)),
        )

    @staticmethod
    def _is_valid_ipv4(value: str) -> bool:
        return all(0 <= int(part) <= 255 for part in value.split("."))

    @staticmethod
    def _replace(
        text: str,
        pattern: re.Pattern[str],
        replacement: str | None,
        category: str,
        counts: dict[str, int],
        *,
        validator: Callable[[str], bool] | None = None,
        formatter: Callable[[re.Match[str]], str] | None = None,
    ) -> str:
        replaced = 0

        def callback(match: re.Match[str]) -> str:
            nonlocal replaced
            value = match.group(0)
            if validator is not None and not validator(value):
                return value
            replaced += 1
            if formatter is not None:
                return formatter(match)
            return replacement or ""

        result = pattern.sub(callback, text)
        if replaced:
            counts[category] = counts.get(category, 0) + replaced
        return result
