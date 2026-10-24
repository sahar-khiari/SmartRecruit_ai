from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

SERVICE_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """Configuration centralisée du service interne.

    Le fichier .env est optionnel. Les variables d'environnement ont priorité.
    Aucune valeur secrète n'est exposée dans les réponses HTTP ou les logs.
    """

    model_config = SettingsConfigDict(
        env_file=SERVICE_ROOT / ".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    ai_service_host: str = "127.0.0.1"
    ai_service_port: int = Field(default=8001, ge=1, le=65535)
    ai_service_api_key: SecretStr = SecretStr("smartrecruit-local-development-key")

    chroma_persist_dir: Path = Path("./data/chroma")
    chroma_collection_name: str = "hr_knowledge"

    embedding_model_name: str = (
        "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    )
    embedding_device: str = "cpu"

    chunk_size: int = Field(default=700, ge=200, le=2000)
    chunk_overlap: int = Field(default=100, ge=0, le=1000)
    default_top_k: int = Field(default=5, ge=1, le=10)
    max_top_k: int = Field(default=10, ge=1, le=20)
    min_relevance_score: float = Field(default=0.30, ge=0.0, le=1.0)

    # --- LLM local Ollama (aucun service cloud) ---
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen3:4b"
    ollama_timeout_seconds: int = Field(default=120, ge=1, le=600)
    ollama_keep_alive: str = "10m"
    ollama_temperature: float = Field(default=0.1, ge=0.0, le=2.0)
    ollama_top_p: float = Field(default=0.9, ge=0.0, le=1.0)
    ollama_num_ctx: int = Field(default=8192, ge=512, le=32768)

    rag_chat_top_k: int = Field(default=3, ge=1, le=5)
    rag_min_relevance_score: float = Field(default=0.35, ge=0.0, le=1.0)
    rag_max_context_chars: int = Field(default=12000, ge=500, le=50000)

    llm_max_answer_chars: int = Field(default=4000, ge=100, le=20000)
    prompt_version: str = "hr-rag-v1"
    llm_max_retries: int = Field(default=1, ge=0, le=1)

    log_level: str = "INFO"

    @field_validator("chroma_persist_dir", mode="before")
    @classmethod
    def resolve_chroma_directory(cls, value: str | Path) -> Path:
        path = Path(value)
        return path if path.is_absolute() else (SERVICE_ROOT / path).resolve()

    @field_validator("ollama_base_url")
    @classmethod
    def normalize_ollama_base_url(cls, value: str) -> str:
        normalized = value.strip().rstrip("/")
        if not normalized.startswith(("http://", "https://")):
            raise ValueError("OLLAMA_BASE_URL doit commencer par http:// ou https://")
        return normalized

    @field_validator("prompt_version")
    @classmethod
    def validate_prompt_version(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("PROMPT_VERSION est obligatoire")
        return normalized

    @field_validator("log_level")
    @classmethod
    def normalize_log_level(cls, value: str) -> str:
        level = value.upper().strip()
        allowed = {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}
        if level not in allowed:
            raise ValueError("LOG_LEVEL doit être un niveau de log Python valide")
        return level

    @model_validator(mode="after")
    def validate_chunking_and_top_k(self) -> "Settings":
        if self.chunk_overlap >= self.chunk_size:
            raise ValueError("CHUNK_OVERLAP doit être inférieur à CHUNK_SIZE")
        if self.default_top_k > self.max_top_k:
            raise ValueError("DEFAULT_TOP_K doit être inférieur ou égal à MAX_TOP_K")
        return self

    @property
    def api_key_value(self) -> str:
        return self.ai_service_api_key.get_secret_value()


@lru_cache
def get_settings() -> Settings:
    return Settings()
