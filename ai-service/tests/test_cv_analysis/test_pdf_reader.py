import pytest

from app.coreAgents.Agents.cv_analysis.constants import MAX_PAGES, MAX_TEXT_CHARS
from app.coreAgents.Agents.cv_analysis.pdf_reader import PdfReadError, read_pdf_text
from .helpers import FICTIONAL_CV, build_pdf


def test_extracts_text_and_page_count():
    result = read_pdf_text(build_pdf(FICTIONAL_CV, pages=2))

    assert result.page_count == 2
    assert "Spring Boot" in result.text
    assert "Développeur" in result.text
    assert result.truncated is False


@pytest.mark.parametrize(
    ("content", "code"),
    [
        (b"", "EMPTY_FILE"),
        (b"Ceci n'est pas un PDF, juste du texte renomme en .pdf", "NOT_PDF"),
        (b"%PDF-1.7 contenu corrompu sans structure", "UNREADABLE"),
    ],
)
def test_rejects_invalid_files(content, code):
    with pytest.raises(PdfReadError) as error:
        read_pdf_text(content)
    assert error.value.code == code


def test_rejects_pdf_without_text():
    with pytest.raises(PdfReadError) as error:
        read_pdf_text(build_pdf([" "]))
    assert error.value.code == "NO_TEXT"


def test_rejects_too_many_pages():
    with pytest.raises(PdfReadError) as error:
        read_pdf_text(build_pdf(FICTIONAL_CV, pages=MAX_PAGES + 1))
    assert error.value.code == "TOO_MANY_PAGES"


def test_truncates_long_text():
    # Environ 26 000 caractères, soit 6 pages : plus long que MAX_TEXT_CHARS, sous MAX_PAGES.
    long_cv = ["Projet réalisé avec Java et Spring Boot pour un client bancaire. " * 20] * 20
    result = read_pdf_text(build_pdf(long_cv))

    assert result.truncated is True
    assert len(result.text) == MAX_TEXT_CHARS
