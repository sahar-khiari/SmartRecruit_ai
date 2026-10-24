"""
Modeles ORM — Module 4 : Tests techniques et feuille de route (TechMatch).
Auteur : Aziz

Tables :
  - tech_tests        : un test genere par le LLM pour un candidat/offre
  - tech_submissions  : reponses soumises par un candidat
  - tech_evaluations  : resultat de correction par le LLM
  - tech_roadmaps     : feuille de route generee
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import DateTime, ForeignKey, Integer, JSON, String, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base

JSONVariant = JSON


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class TechTestModel(Base):
    """Un test technique genere par le LLM. Reponses correctes stockees ici (jamais envoyees au front)."""
    __tablename__ = "tech_tests"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    candidate_id: Mapped[str] = mapped_column(String(100), index=True)
    job_title: Mapped[str] = mapped_column(String(200))
    job_description: Mapped[str] = mapped_column(Text)
    competences_manquantes: Mapped[list] = mapped_column(JSONVariant)
    test_data: Mapped[dict] = mapped_column(JSONVariant)
    llm_profile: Mapped[str | None] = mapped_column(String(16), nullable=True)
    llm_latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    prompt_version: Mapped[str | None] = mapped_column(String(10), nullable=True)
    status: Mapped[str] = mapped_column(String(20), default="generated")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, onupdate=_utcnow)

    # Relations — SQLAlchemy sait faire le JOIN grace aux ForeignKey ci-dessous
    submissions: Mapped[list["TechSubmissionModel"]] = relationship(back_populates="test")
    evaluations: Mapped[list["TechEvaluationModel"]] = relationship(back_populates="test")


class TechSubmissionModel(Base):
    """Reponses d'un candidat a un test technique."""
    __tablename__ = "tech_submissions"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)

    # ForeignKey correctement declare — SQLAlchemy peut maintenant faire le JOIN
    test_id: Mapped[uuid.UUID] = mapped_column(
        Uuid, ForeignKey("tech_tests.id", ondelete="CASCADE"), nullable=False, index=True
    )
    candidate_id: Mapped[str] = mapped_column(String(100), index=True)
    reponses_questions: Mapped[list] = mapped_column(JSONVariant)
    reponse_exercice: Mapped[str] = mapped_column(Text)
    submitted_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    # Relation inverse
    test: Mapped[TechTestModel] = relationship(back_populates="submissions")
    evaluations: Mapped[list["TechEvaluationModel"]] = relationship(back_populates="submission")


class TechEvaluationModel(Base):
    """Resultat de la correction par le LLM."""
    __tablename__ = "tech_evaluations"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)

    # ForeignKeys correctement declares
    test_id: Mapped[uuid.UUID] = mapped_column(
        Uuid, ForeignKey("tech_tests.id", ondelete="CASCADE"), nullable=False, index=True
    )
    submission_id: Mapped[uuid.UUID] = mapped_column(
        Uuid, ForeignKey("tech_submissions.id", ondelete="CASCADE"), nullable=False, index=True
    )
    candidate_id: Mapped[str] = mapped_column(String(100), index=True)
    evaluation_data: Mapped[dict] = mapped_column(JSONVariant)
    score: Mapped[int] = mapped_column(Integer)
    recommandation: Mapped[str] = mapped_column(String(20))
    llm_latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    prompt_version: Mapped[str | None] = mapped_column(String(10), nullable=True)
    evaluated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    # Relations
    test: Mapped[TechTestModel] = relationship(back_populates="evaluations")
    submission: Mapped[TechSubmissionModel] = relationship(back_populates="evaluations")


class TechRoadmapModel(Base):
    """Feuille de route d'apprentissage generee par le LLM."""
    __tablename__ = "tech_roadmaps"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    candidate_id: Mapped[str] = mapped_column(String(100), index=True)
    competences_manquantes: Mapped[list] = mapped_column(JSONVariant)
    evaluation_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    roadmap_data: Mapped[dict] = mapped_column(JSONVariant)
    llm_latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    prompt_version: Mapped[str | None] = mapped_column(String(10), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
