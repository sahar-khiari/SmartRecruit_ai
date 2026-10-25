"""
Configuration du module matching.
Toutes les variables sont optionnelles avec des valeurs par défaut sûres.
Le module fonctionne sans LLM (MATCHING_LLM_ENABLED=false par défaut).
"""
import os
from functools import lru_cache
from pydantic_settings import BaseSettings


class MatchingSettings(BaseSettings):
    # LLM configurable pour le module matching (indépendant des autres modules)
    matching_llm_enabled: bool = False
    matching_llm_base_url: str = "http://localhost:11434/v1"
    matching_llm_api_key: str = "not-required-for-local"
    matching_llm_model: str = "llama3"
    matching_llm_timeout: int = 30
    matching_llm_max_retries: int = 1

    # Embedding model pour la couche 1
    matching_embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    matching_embedding_device: str = "cpu"

    # Prompt version
    matching_prompt_version: str = "matching-v1"

    class Config:
        env_file = ".env"
        extra = "ignore"


@lru_cache(maxsize=1)
def get_matching_settings() -> MatchingSettings:
    return MatchingSettings()
