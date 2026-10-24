import pytest

from app.services.text_cleaning_service import TextCleaningService


@pytest.fixture
def service() -> TextCleaningService:
    return TextCleaningService()


def test_collapses_spaces_and_blank_lines(service: TextCleaningService) -> None:
    result = service.clean("  Bonjour   le   monde \n\n\n\n Deuxième   ligne  ")
    assert result == "Bonjour le monde\n\nDeuxième ligne"


def test_normalizes_windows_line_endings(service: TextCleaningService) -> None:
    assert service.clean("Ligne 1\r\nLigne 2\rLigne 3") == "Ligne 1\nLigne 2\nLigne 3"


def test_removes_control_characters(service: TextCleaningService) -> None:
    assert service.clean("Bonjour\x00\x07 le monde") == "Bonjour le monde"


def test_preserves_french_accents(service: TextCleaningService) -> None:
    text = "Le candidat prépare son entretien à l'étranger : déjà prêt."
    assert service.clean(text) == text


def test_preserves_arabic_characters(service: TextCleaningService) -> None:
    text = "مرحبا بكم في منصة التوظيف"
    assert service.clean(text) == text


def test_rejects_empty_text(service: TextCleaningService) -> None:
    with pytest.raises(ValueError, match="vide"):
        service.clean(" \n\t \n ")


def test_rejects_none(service: TextCleaningService) -> None:
    with pytest.raises(ValueError, match="obligatoire"):
        service.clean(None)
