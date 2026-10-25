"""
Router FastAPI : Module 1, analyse de CV.
Auteur : Jalel Nasr (branche module1-candidats)

Expose :
  POST /api/v1/cv/analyze
"""
from fastapi import APIRouter

from app.v1.endpoints.cv_analysis import router as _cv_router

router = APIRouter()
router.include_router(_cv_router, prefix="/api/v1")
