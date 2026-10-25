import pytest
from pydantic import ValidationError

from app.coreAgents.Agents.cv_analysis.profiler import experience_level_from_years
from app.v1.schemas.cv_analysis import CvLlmExtraction


def test_cleans_llm_output():
    data = CvLlmExtraction.model_validate({
        "headline": "  Développeur Java  ",
        "summary": "[NAME] est développeur Java chez [LOCATION].",
        "years_of_experience": 4,
        "skills": ["Java", "java", " Spring  Boot ", "", "[NAME]", "x" * 81],
        "soft_skills": [],
        "languages": ["Anglais"],
    })

    assert data.headline == "Développeur Java"
    assert "[" not in data.summary
    assert data.skills == ["Java", "Spring Boot"]


@pytest.mark.parametrize("years", [-1, 61])
def test_rejects_impossible_years(years):
    with pytest.raises(ValidationError):
        CvLlmExtraction.model_validate({"years_of_experience": years})


@pytest.mark.parametrize(
    ("years", "level"),
    [(None, None), (0, "JUNIOR"), (2, "JUNIOR"), (3, "INTERMEDIATE"), (5, "INTERMEDIATE"), (6, "SENIOR")],
)
def test_level_rule_matches_spring(years, level):
    assert experience_level_from_years(years) == level
