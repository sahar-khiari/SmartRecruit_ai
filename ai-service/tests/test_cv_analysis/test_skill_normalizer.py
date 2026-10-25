import pytest

from app.coreAgents.Agents.cv_analysis.skill_normalizer import SkillNormalizer, get_skill_normalizer


@pytest.fixture(scope="module")
def normalizer() -> SkillNormalizer:
    return get_skill_normalizer()


@pytest.mark.parametrize(
    ("raw", "name", "method"),
    [
        ("ReactJS", "React", "EXACT"),
        ("react.js", "React", "EXACT"),
        ("Spring-Boot", "Spring Boot", "EXACT"),
        ("SpringBoot", "Spring Boot", "EXACT"),
        ("MySql", "MySQL", "EXACT"),
        ("Postgres", "PostgreSQL", "EXACT"),
        ("REST API", "API REST", "EXACT"),
        ("node js", "Node.js", "EXACT"),
        ("Python 3.11", "Python", "VERSION"),
        ("Kubernete", "Kubernetes", "FUZZY"),
        ("Javascrip", "JavaScript", "FUZZY"),
    ],
)
def test_variants_get_official_name(normalizer, raw, name, method):
    result = normalizer.normalize_one(raw)
    assert (result.name, result.method) == (name, method)
    assert result.category is not None


@pytest.mark.parametrize(("raw", "name"), [("C", "C"), ("C#", "C#"), ("C++", "C++"), ("Go", "Go"), ("Git", "Git")])
def test_short_names_are_not_confused(normalizer, raw, name):
    assert normalizer.normalize_one(raw).name == name


def test_unknown_skill_is_kept_as_written(normalizer):
    result = normalizer.normalize_one("  Odoo  ")
    assert (result.name, result.category, result.method) == ("Odoo", None, "UNKNOWN")


def test_duplicates_are_merged_in_cv_order(normalizer):
    names = [s.name for s in normalizer.normalize(["ReactJS", "Java", "react", "JAVA 17", "Docker", ""])]
    assert names == ["React", "Java", "Docker"]


def test_referential_rejects_ambiguous_alias():
    with pytest.raises(ValueError, match="double"):
        SkillNormalizer({"version": "t", "skills": [
            {"name": "React", "category": "FRAMEWORK", "aliases": ["rjs"]},
            {"name": "Redux", "category": "FRAMEWORK", "aliases": ["rjs"]},
        ]})
