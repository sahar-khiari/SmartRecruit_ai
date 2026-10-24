from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from collections.abc import Generator
from app.config import settings

engine = create_engine(
    settings.DATABASE_URL,
    # SQLite nécessite ce paramètre pour les threads (FastAPI est async)
    connect_args={"check_same_thread": False} if "sqlite" in settings.DATABASE_URL else {},
)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def get_db() -> Generator[Session, None, None]:
    """Dépendance FastAPI : fournit une session BDD par requête."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
