"""Configuration propre au Module 1, séparée de la configuration commune (app/core/config.py).
Elle lit le même fichier .env ; aucune clé n'est journalisée ni renvoyée."""

from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

from app.core.config import SERVICE_ROOT


class CvAnalysisSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=SERVICE_ROOT / ".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    gemini_api_key: SecretStr | None = None
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    # Modèle principal (plus précis) et modèle de secours (plus léger, quota séparé).
    gemini_model: str = "gemini-3.5-flash"
    gemini_fallback_model: str = "gemini-3.5-flash-lite"
    gemini_timeout_seconds: float = Field(default=45, ge=5, le=120)
    gemini_temperature: float = Field(default=0.1, ge=0.0, le=1.0)
    # Nouvel essai sur le même modèle après une erreur passagère (réseau, 503) ou un JSON invalide.
    llm_max_retries: int = Field(default=1, ge=0, le=2)

    @property
    def gemini_key_value(self) -> str | None:
        if self.gemini_api_key is None:
            return None
        return self.gemini_api_key.get_secret_value().strip() or None


@lru_cache
def get_cv_settings() -> CvAnalysisSettings:
    return CvAnalysisSettings()
