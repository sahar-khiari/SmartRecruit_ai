"""Anonymisation avec le vrai modèle spaCy (local, aucun appel réseau)."""

import pytest

from app.coreAgents.Agents.cv_analysis.anonymizer import CvAnonymizer

CV_TEXT = """AHMED BEN SALAH
ahmed.bensalah@example.com | +216 22 333 444 | 98 765 432
https://github.com/ahmed-bs
Développeur Full Stack Java / Angular
2021 - 2024 : Développeur chez Vermeg, Tunis. Ahmed a développé des API REST avec Spring Boot.
Compétences : Java, Spring Boot, Angular, Docker, MySQL, Git
Référence : Mme Leila Trabelsi, responsable technique."""


@pytest.fixture(scope="module")
def result():
    return CvAnonymizer().anonymize(CV_TEXT)


def test_masks_contact_details(result):
    assert "ahmed.bensalah@example.com" not in result.text
    assert "github.com" not in result.text
    assert "22 333 444" not in result.text
    assert "98 765 432" not in result.text, "téléphone tunisien sans indicatif"
    assert "[EMAIL]" in result.text and "[PHONE]" in result.text and "[URL]" in result.text


def test_masks_header_name_and_its_later_mentions(result):
    text = result.text.lower()
    assert "ahmed" not in text
    assert "salah" not in text
    assert result.text.startswith("[NAME]")


def test_masks_other_people(result):
    assert "Leila" not in result.text
    assert "Trabelsi" not in result.text


def test_keeps_skills_and_years(result):
    for skill in ("Java", "Spring Boot", "Angular", "Docker", "MySQL", "Git", "API REST"):
        assert skill in result.text
    assert "2021 - 2024" in result.text


def test_reports_categories_without_values(result):
    assert {"EMAIL", "PHONE", "URL", "NAME"} <= set(result.categories)
    assert result.replaced_count >= 6


def test_keeps_cv_section_titles():
    text = CvAnonymizer().anonymize(
        "Ahmed Ben Salah\nCompétences : Java, Spring Boot, Docker\nFormation : ESPRIT\nLangues : Anglais"
    ).text
    assert "Compétences : Java" in text
    assert "Formation :" in text and "Langues :" in text
    assert "[LOCATION] :" not in text


def test_header_that_is_not_a_name_is_kept():
    text = CvAnonymizer().anonymize("Curriculum Vitae\nCompétences : Java, Python, Docker").text
    assert "Java" in text and "Python" in text
