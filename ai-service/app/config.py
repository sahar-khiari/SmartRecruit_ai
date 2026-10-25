"""
Configuration — Module 4 TechMatch.
Lit les variables depuis le fichier .env (jamais en dur dans le code).
"""
import os
from dotenv import load_dotenv

load_dotenv()


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


class Settings:
    # LLM (Groq en dev, OpenAI-compatible)
    LLM_API_KEY: str = os.getenv("LLM_API_KEY", "")
    LLM_BASE_URL: str = os.getenv("LLM_BASE_URL", "https://api.groq.com/openai")
    LLM_MODEL: str = os.getenv("LLM_MODEL", "llama-3.3-70b-versatile")
    LLM_TIMEOUT: int = _env_int("LLM_TIMEOUT_SECONDS", 60)

    # Base de données — si DATABASE_URL est vide ou absent → SQLite automatique
    # Pas besoin de configurer quoi que ce soit pour tester en local !
    DATABASE_URL: str = os.getenv("DATABASE_URL", "") or "sqlite:///./techmatch.db"


settings = Settings()
