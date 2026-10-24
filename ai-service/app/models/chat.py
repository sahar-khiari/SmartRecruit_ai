from pydantic import BaseModel, ConfigDict, Field
from app.models.knowledge import KnowledgeCategory


class RagGenerationOutput(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    answer: str
    category: KnowledgeCategory | str
    grounded: bool
    needs_human_review: bool = Field(alias="needsHumanReview")
    used_chunk_ids: list[str] = Field(default_factory=list, alias="usedChunkIds")


class RagSource(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    chunk_id: str = Field(alias="chunkId")
    document_id: int = Field(alias="documentId")
    title: str
    category: KnowledgeCategory | str
    score: float
    excerpt: str


class RagChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    question: str
    user_role: str | None = Field(default=None, alias="userRole")
    top_k: int | None = Field(default=None, alias="topK")
    category_hints: list[KnowledgeCategory | str] | None = Field(default=None, alias="categoryHints")


class RagChatResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    answer: str
    category: KnowledgeCategory | str
    grounded: bool
    needs_human_review: bool = Field(alias="needsHumanReview")
    sources: list[RagSource] = Field(default_factory=list)
    model_name: str = Field(alias="modelName")
    prompt_version: str = Field(alias="promptVersion")
    total_duration_ms: int = Field(alias="totalDurationMs")
    retrieval_duration_ms: int = Field(default=0, alias="retrievalDurationMs")
    generation_duration_ms: int = Field(default=0, alias="generationDurationMs")
