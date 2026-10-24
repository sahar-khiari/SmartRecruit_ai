import re


class TextCleaningService:
    """Normalise le texte sans le résumer ni le reformuler.

    Les accents, l'arabe et les autres écritures Unicode sont conservés.
    Seuls les caractères de contrôle et les espaces superflus sont éliminés.
    """

    _CONTROL_CHARACTERS = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
    _HORIZONTAL_SPACES = re.compile(r"[ \t\u00a0\u202f]+")

    def clean(self, text: str | None) -> str:
        if text is None:
            raise ValueError("Le texte est obligatoire")

        normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        normalized = self._CONTROL_CHARACTERS.sub("", normalized)

        lines: list[str] = []
        previous_was_blank = False
        for raw_line in normalized.split("\n"):
            line = self._HORIZONTAL_SPACES.sub(" ", raw_line).strip()
            if line:
                lines.append(line)
                previous_was_blank = False
            elif lines and not previous_was_blank:
                lines.append("")
                previous_was_blank = True

        cleaned = "\n".join(lines).strip()
        if not cleaned:
            raise ValueError("Le document est vide après nettoyage")
        return cleaned
