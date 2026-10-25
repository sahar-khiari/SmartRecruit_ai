import time
import uuid
from pydantic import ValidationError
from app.coreAgents.Agents.tech_assessment.prompts import OUTPUT_SCHEMA_EVALUATE, build_prompt_evaluate
from app.coreAgents.llm_client import LLMError, _appeler_llm
from app.v1.schemas.tech_assessment import EvaluationResult, SubmitTestRequest, SubmitTestResponse


class TechEvaluationError(Exception):
    pass


class TechTestEvaluator:
    def __init__(self, session_factory=None, llm_client=None):
        if session_factory is None:
            from app.db.session import SessionLocal
            session_factory = SessionLocal
        self._session_factory = session_factory
        self._mock_llm = llm_client

    def evaluate(self, test_id: str, request: SubmitTestRequest) -> SubmitTestResponse:
        """Corrige les reponses du candidat via le LLM."""
        test_data = self._charger_test(test_id)
        reponses_list = [r.model_dump() for r in request.reponses_questions]
        messages = build_prompt_evaluate(
            test_data=test_data,
            reponses_questions=reponses_list,
            reponse_exercice=request.reponse_exercice,
        )
        debut = time.perf_counter()
        try:
            if self._mock_llm is not None:
                raw_json = self._mock_llm.chat_json(
                    profile=None, messages=messages, caller="", json_schema=None
                )
            else:
                raw_json = _appeler_llm(messages, json_schema=OUTPUT_SCHEMA_EVALUATE)
        except LLMError as exc:
            raise TechEvaluationError(
                "Le service d evaluation est temporairement indisponible : " + str(exc)
            ) from exc
        except Exception as exc:
            raise TechEvaluationError(str(exc)) from exc
        latence_ms = int((time.perf_counter() - debut) * 1000)

        try:
            evaluation = EvaluationResult(**raw_json)
        except (ValidationError, Exception) as exc:
            raise TechEvaluationError(
                "Le resultat d evaluation ne respecte pas le schema : " + str(exc)
            ) from exc

        submission_id = str(uuid.uuid4())
        self._persister_evaluation(test_id, submission_id, request, evaluation, latence_ms)
        self._marquer_evalue(test_id)
        return SubmitTestResponse(
            test_id=test_id,
            candidate_id=request.candidate_id,
            evaluation=evaluation,
        )

    def _charger_test(self, test_id: str) -> dict:
        try:
            from app.db.models.tech_assessment import TechTestModel
            db = self._session_factory()
            try:
                modele = db.query(TechTestModel).filter(
                    TechTestModel.id == uuid.UUID(test_id)
                ).first()
            finally:
                db.close()
            if modele is None:
                raise TechEvaluationError("Test introuvable : " + test_id)
            return modele.test_data
        except TechEvaluationError:
            raise
        except Exception as exc:
            raise TechEvaluationError("Erreur de lecture du test en base : " + str(exc)) from exc

    def _persister_evaluation(self, test_id, submission_id, request, evaluation, latence_ms):
        try:
            from app.db.models.tech_assessment import TechEvaluationModel, TechSubmissionModel
            db = self._session_factory()
            try:
                db.add(TechSubmissionModel(
                    id=uuid.UUID(submission_id),
                    test_id=uuid.UUID(test_id),
                    candidate_id=request.candidate_id,
                    reponses_questions=[r.model_dump() for r in request.reponses_questions],
                    reponse_exercice=request.reponse_exercice,
                ))
                db.add(TechEvaluationModel(
                    id=uuid.uuid4(),
                    test_id=uuid.UUID(test_id),
                    submission_id=uuid.UUID(submission_id),
                    candidate_id=request.candidate_id,
                    evaluation_data=evaluation.model_dump(),
                    score=evaluation.score,
                    recommandation=evaluation.recommandation,
                    llm_latency_ms=latence_ms,
                ))
                db.commit()
            finally:
                db.close()
        except Exception:
            import logging
            logging.getLogger(__name__).exception("Echec persistance evaluation")

    def _marquer_evalue(self, test_id: str):
        try:
            from app.db.models.tech_assessment import TechTestModel
            db = self._session_factory()
            try:
                m = db.query(TechTestModel).filter(TechTestModel.id == uuid.UUID(test_id)).first()
                if m:
                    m.status = "evaluated"
                    db.commit()
            finally:
                db.close()
        except Exception:
            pass
