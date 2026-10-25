"""Étape 1 du pipeline : extraction du texte d'un CV PDF avec pdfplumber (pas d'IA)."""

import io
import logging
from dataclasses import dataclass

import pdfplumber

from app.coreAgents.Agents.cv_analysis.constants import (
    MAX_PAGES,
    MAX_PDF_BYTES,
    MAX_TEXT_CHARS,
    MIN_TEXT_CHARS,
)

logger = logging.getLogger(__name__)

PDF_SIGNATURE = b"%PDF-"


class PdfReadError(ValueError):
    """PDF refusé. `code` est stable (utilisé par Spring et les tests), le message est lisible."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


@dataclass(frozen=True)
class PdfText:
    text: str
    page_count: int
    truncated: bool


def read_pdf_text(content: bytes) -> PdfText:
    if not content:
        raise PdfReadError("EMPTY_FILE", "Le fichier est vide")
    if len(content) > MAX_PDF_BYTES:
        raise PdfReadError("TOO_LARGE", "Le CV ne doit pas dépasser 5 Mo")
    if not content.startswith(PDF_SIGNATURE):
        raise PdfReadError("NOT_PDF", "Le fichier n'est pas un PDF valide")

    try:
        with pdfplumber.open(io.BytesIO(content)) as pdf:
            page_count = len(pdf.pages)
            if page_count > MAX_PAGES:
                raise PdfReadError("TOO_MANY_PAGES", f"Le CV ne doit pas dépasser {MAX_PAGES} pages")
            pages = [page.extract_text() or "" for page in pdf.pages]
    except PdfReadError:
        raise
    except Exception as exc:  # pdfminer lève des exceptions variées (PDF corrompu, chiffré...)
        logger.warning("PDF illisible | type=%s", type(exc).__name__)
        raise PdfReadError("UNREADABLE", "Le PDF est illisible, corrompu ou protégé par mot de passe") from exc

    text = "\n\n".join(page.strip() for page in pages if page.strip())
    if len(text) < MIN_TEXT_CHARS:
        raise PdfReadError(
            "NO_TEXT",
            "Le PDF ne contient pas de texte exploitable (CV scanné ?). Exportez votre CV en PDF texte.",
        )

    truncated = len(text) > MAX_TEXT_CHARS
    return PdfText(text=text[:MAX_TEXT_CHARS], page_count=page_count, truncated=truncated)
