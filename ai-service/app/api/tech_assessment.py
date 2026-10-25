"""
Router FastAPI — Module 4 : Tests techniques et Feuille de route IA.
Auteur : Aziz (branche module4-aziz)

Expose :
  POST /api/v1/tests/generate
  POST /api/v1/tests/{test_id}/submit
  POST /api/v1/roadmap
"""
from fastapi import APIRouter

from app.v1.endpoints.tech_assessment import (
    router as _tests_router,
    roadmap_router as _roadmap_router,
)

router = APIRouter()
router.include_router(_tests_router, prefix="/api/v1")
router.include_router(_roadmap_router, prefix="/api/v1")
