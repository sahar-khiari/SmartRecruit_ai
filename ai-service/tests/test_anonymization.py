import pytest

from app.services.anonymization_service import AnonymizationService


@pytest.fixture
def service() -> AnonymizationService:
    return AnonymizationService()


def test_replaces_email(service: AnonymizationService) -> None:
    result = service.anonymize("Contactez jean.dupont@example.com pour la suite.")
    assert "[EMAIL]" in result.text
    assert "jean.dupont@example.com" not in result.text
    assert result.replaced_count == 1
    assert "EMAIL" in result.categories


def test_replaces_phone(service: AnonymizationService) -> None:
    result = service.anonymize("Appelez le 06 12 34 56 78 ou +33 6 98 76 54 32.")
    assert result.text.count("[PHONE]") == 2
    assert "06 12 34 56 78" not in result.text
    assert result.replaced_count == 2


def test_replaces_url(service: AnonymizationService) -> None:
    result = service.anonymize("Voir https://example.com/offres et www.smartrecruit.fr.")
    assert result.text.count("[URL]") == 2
    assert "example.com" not in result.text


def test_replaces_ip(service: AnonymizationService) -> None:
    result = service.anonymize("Serveur 192.168.1.42 et 10.0.0.1.")
    assert result.text.count("[IP]") == 2
    assert "192.168.1.42" not in result.text


def test_does_not_replace_invalid_ip(service: AnonymizationService) -> None:
    result = service.anonymize("Version 999.999.999.999.")
    assert "[IP]" not in result.text


def test_replaces_french_national_identifier(service: AnonymizationService) -> None:
    result = service.anonymize("NIR 1 85 03 75 123 456 78 à vérifier.")
    assert "[IDENTIFIER]" in result.text
    assert "1 85 03 75 123 456 78" not in result.text


def test_replaces_labeled_identifier(service: AnonymizationService) -> None:
    result = service.anonymize("CIN : AB123456 pour le dossier.")
    assert "[IDENTIFIER]" in result.text
    assert "AB123456" not in result.text


def test_does_not_treat_cin_prefix_as_identifier(service: AnonymizationService) -> None:
    text = "Une décision est communiquée sous cinq jours ouvrés."
    result = service.anonymize(text)
    assert result.text == text
    assert result.replaced_count == 0


def test_replaces_labeled_name(service: AnonymizationService) -> None:
    result = service.anonymize("Nom : Dupont\nPrénom : Marie")
    assert "Dupont" not in result.text
    assert "Marie" not in result.text
    assert result.text.count("[NAME]") == 2


def test_replaces_titled_name(service: AnonymizationService) -> None:
    result = service.anonymize("Monsieur Dupont a envoyé sa candidature.")
    assert "Dupont" not in result.text
    assert "Monsieur [NAME]" in result.text


def test_replaces_labeled_secret_and_jwt(service: AnonymizationService) -> None:
    result = service.anonymize(
        "Mot de passe : Secret123, token=abcDEF456 et JWT "
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.signature123."
    )
    assert "Secret123" not in result.text
    assert "abcDEF456" not in result.text
    assert "eyJhbGciOiJIUzI1NiJ9" not in result.text
    assert "[SECRET]" in result.text
    assert "[TOKEN]" in result.text


def test_does_not_mask_generic_password_policy(service: AnonymizationService) -> None:
    text = "Le mot de passe est requis et doit être robuste."
    result = service.anonymize(text)
    assert result.text == text
    assert result.replaced_count == 0


def test_general_hr_text_is_not_degraded(service: AnonymizationService) -> None:
    text = (
        "Le processus de recrutement comporte un entretien téléphonique, "
        "un test technique et un entretien final."
    )
    result = service.anonymize(text)
    assert result.text == text
    assert result.replaced_count == 0
    assert result.categories == ()


def test_diagnostic_does_not_contain_raw_values(service: AnonymizationService) -> None:
    result = service.anonymize(
        "Email a@b.fr, téléphone 06 11 22 33 44, IP 192.168.0.1, Nom : Martin."
    )
    diagnostics = f"{result.replaced_count} {result.categories} {result.text}"
    assert "a@b.fr" not in diagnostics
    assert "06 11 22 33 44" not in diagnostics
    assert "192.168.0.1" not in diagnostics
    assert "Martin" not in diagnostics
