from pydantic import BaseModel, ConfigDict, Field
from app.models.knowledge import KnowledgeCategory


class RetrievalSearchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    query: str
    top_k: int = Field(default=5, alias="topK")
    categories: list[KnowledgeCategory | str] | None = None
    document_ids: list[int] | None = Field(default=None, alias="documentIds")

    def normalized_document_ids(self) -> list[int] | None:
        return self.document_ids


class RetrievedChunk(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    document_id: int = Field(alias="documentId")
    chunk_id: str = Field(alias="chunkId")
    chunk_index: int = Field(alias="chunkIndex")
    title: str
    category: KnowledgeCategory | str
    content: str
    score: float
    version: int


class RetrievalSearchResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    query: str
    sanitized_query: str = Field(alias="sanitizedQuery")
    results: list[RetrievedChunk]
    total_results: int = Field(alias="totalResults")
    model_name: str = Field(alias="modelName")
    duration_ms: int = Field(alias="durationMs")
