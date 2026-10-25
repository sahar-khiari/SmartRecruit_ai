import time
import uuid
from pydantic import ValidationError
from app.coreAgents.Agents.tech_assessment.prompts import (
    OUTPUT_SCHEMA_GENERATE, PromptVersion, get_generate_messages
)
from app.coreAgents.llm_client import LLMError, _appeler_llm
from app.v1.schemas.tech_assessment import GenerateTestRequest, TechTest, TechTestPublic


class TechTestGenerationError(Exception):
    pass


class TechTestGenerator:
    def __init__(self, session_factory=None, llm_client=None):
        if session_factory is None:
            from app.db.session import SessionLocal
            session_factory = SessionLocal
        self._session_factory = session_factory
        # llm_client permet d'injecter un mock dans les tests unitaires
        self._mock_llm = llm_client

    def generate(self, request: GenerateTestRequest, prompt_version: PromptVersion = "v2"):
        """Genere un test, valide le JSON Pydantic, persiste en BDD."""
        messages = get_generate_messages(
            job_title=request.job_title,
            job_description=request.job_description,
            competences=request.competences_manquantes,
            version=prompt_version,
        )
        debut = time.perf_counter()
        try:
            if self._mock_llm is not None:
                raw_json = self._mock_llm.chat_json(
                    profile=None, messages=messages, caller="", json_schema=None
                )
            else:
                raw_json = _appeler_llm(messages, json_schema=OUTPUT_SCHEMA_GENERATE)
        except LLMError as exc:
            raise TechTestGenerationError(
                "Le service de generation est temporairement indisponible : " + str(exc)
            ) from exc
        except Exception as exc:
            raise TechTestGenerationError(
                "La reponse du modele IA n est pas un JSON valide : " + str(exc)
            ) from exc
        latence_ms = int((time.perf_counter() - debut) * 1000)

        try:
            test = TechTest(**raw_json)
        except (ValidationError, Exception) as exc:
            raise TechTestGenerationError(
                "Le test genere ne respecte pas le schema attendu : " + str(exc)
            ) from exc

        test_id = str(uuid.uuid4())
        self._persister_test(test_id, request, test, latence_ms, prompt_version)
        test_public = TechTestPublic.depuis_test(test_id=test_id, test=test)
        return test, test_public, test_id

    def _persister_test(self, test_id, request, test, latence_ms, prompt_version):
        try:
            from app.db.models.tech_assessment import TechTestModel
            db = self._session_factory()
            try:
                db.add(TechTestModel(
                    id=uuid.UUID(test_id),
                    candidate_id=request.candidate_id,
                    job_title=request.job_title,
                    job_description=request.job_description,
                    competences_manquantes=[c.model_dump() for c in request.competences_manquantes],
                    test_data=test.model_dump(),
                    llm_latency_ms=latence_ms,
                    prompt_version=prompt_version,
                    status="generated",
                ))
                db.commit()
            finally:
                db.close()
        except Exception:
            import logging
            logging.getLogger(__name__).exception("Echec persistance test %s", test_id)
