"""Configuration du Module 2, isolée des réglages des autres modules.

Les valeurs sont lues dans les variables d'environnement ou dans le fichier .env.
La clé Cohere n'est jamais écrite dans le code ni renvoyée dans une réponse.
"""
from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class OffresSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    cohere_api_key: SecretStr | None = None
    cohere_base_url: str = "https://api.cohere.com"
    # Modèle de génération (chat) et modèle d'embeddings multilingue (le français est bien géré).
    cohere_chat_model: str = "command-r-08-2024"
    cohere_embed_model: str = "embed-multilingual-v3.0"
    cohere_timeout_seconds: int = Field(default=30, ge=1, le=120)
    # Nombre de nouvelles tentatives si la réponse est invalide ou si le délai est dépassé.
    cohere_max_retries: int = Field(default=2, ge=0, le=3)
    offres_cache_size: int = Field(default=128, ge=0, le=2000)

    @property
    def cohere_key_value(self) -> str | None:
        if self.cohere_api_key is None:
            return None
        value = self.cohere_api_key.get_secret_value().strip()
        if not value or value.startswith("REMPLACE"):
            return None
        return value


@lru_cache
def get_offres_settings() -> OffresSettings:
    return OffresSettings()
