import time
import uuid
from pydantic import ValidationError
from app.coreAgents.Agents.tech_assessment.prompts import OUTPUT_SCHEMA_ROADMAP, build_prompt_roadmap
from app.coreAgents.llm_client import LLMError, _appeler_llm
from app.v1.schemas.tech_assessment import EvaluationResult, RoadmapRequest, RoadmapResponse, RoadmapResult


class RoadmapGenerationError(Exception):
    pass


class RoadmapGenerator:
    def __init__(self, session_factory=None, llm_client=None):
        if session_factory is None:
            from app.db.session import SessionLocal
            session_factory = SessionLocal
        self._session_factory = session_factory
        self._mock_llm = llm_client

    def generate(self, request: RoadmapRequest) -> RoadmapResponse:
        eval_summary = self._resumer_evaluation(request.evaluation)
        messages = build_prompt_roadmap(
            competences=request.competences_manquantes,
            evaluation_summary=eval_summary,
            duree_semaines=request.duree_plan_semaines,
        )
        debut = time.perf_counter()
        try:
            if self._mock_llm is not None:
                raw_json = self._mock_llm.chat_json(
                    profile=None, messages=messages, caller="", json_schema=None
                )
            else:
                raw_json = _appeler_llm(messages, json_schema=OUTPUT_SCHEMA_ROADMAP)
        except LLMError as exc:
            raise RoadmapGenerationError(
                "Le service de generation de feuille de route est indisponible : " + str(exc)
            ) from exc
        except Exception as exc:
            raise RoadmapGenerationError(str(exc)) from exc
        latence_ms = int((time.perf_counter() - debut) * 1000)

        try:
            roadmap = RoadmapResult(**raw_json)
        except (ValidationError, Exception) as exc:
            raise RoadmapGenerationError(
                "La feuille de route ne respecte pas le schema : " + str(exc)
            ) from exc

        self._persister_roadmap(request, roadmap, latence_ms)
        return RoadmapResponse(candidate_id=request.candidate_id, roadmap=roadmap)

    @staticmethod
    def _resumer_evaluation(evaluation: EvaluationResult | None) -> str | None:
        if evaluation is None:
            return None
        return (
            f"Score : {evaluation.score}/100. "
            f"Recommandation : {evaluation.recommandation}. "
            f"Points faibles : {', '.join(evaluation.points_faibles[:3])}."
        )

    def _persister_roadmap(self, request, roadmap, latence_ms):
        try:
            from app.db.models.tech_assessment import TechRoadmapModel
            db = self._session_factory()
            try:
                db.add(TechRoadmapModel(
                    id=uuid.uuid4(),
                    candidate_id=request.candidate_id,
                    competences_manquantes=[c.model_dump() for c in request.competences_manquantes],
                    roadmap_data=roadmap.model_dump(),
                    llm_latency_ms=latence_ms,
                ))
                db.commit()
            finally:
                db.close()
        except Exception:
            import logging
            logging.getLogger(__name__).exception("Echec persistance roadmap")
