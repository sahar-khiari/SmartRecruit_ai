from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field


class HealthResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    status: str
    service: str = "smartrecruit-ai-service"
    version: str = "1.1.0"
    embedding_model: str = Field(alias="embeddingModel")
    chroma_available: bool = Field(alias="chromaAvailable")
    ollama_available: bool = Field(alias="ollamaAvailable")
    llm_model: str = Field(alias="llmModel")
    llm_model_available: bool = Field(alias="llmModelAvailable")
    timestamp: datetime
