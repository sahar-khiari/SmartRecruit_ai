from datetime import datetime
from enum import Enum
from typing import Self
from pydantic import BaseModel, ConfigDict, Field


class KnowledgeCategory(str, Enum):
    FAQ = "FAQ"
    CANDIDATE_GUIDE = "CANDIDATE_GUIDE"
    RECRUITER_GUIDE = "RECRUITER_GUIDE"
    INTERVIEW_GUIDE = "INTERVIEW_GUIDE"
    RECRUITMENT_PROCEDURE = "RECRUITMENT_PROCEDURE"
    PLATFORM_HELP = "PLATFORM_HELP"
    HR_POLICY = "HR_POLICY"
    OTHER = "OTHER"


class IndexingStatus(str, Enum):
    INDEXED = "INDEXED"
    NOT_INDEXED = "NOT_INDEXED"


class DeleteStatus(str, Enum):
    DELETED = "DELETED"
    NOT_FOUND = "NOT_FOUND"


class KnowledgeDocumentIndexRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    document_id: int = Field(alias="documentId")
    title: str
    content: str
    category: KnowledgeCategory | str
    version: int = 1
    active: bool = True


class KnowledgeDocumentIndexResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    document_id: int = Field(alias="documentId")
    version: int
    status: IndexingStatus
    chunks_indexed: int = Field(alias="chunksIndexed")
    anonymized_items_count: int = Field(alias="anonymizedItemsCount")
    model_name: str = Field(alias="modelName")
    indexed_at: datetime = Field(alias="indexedAt")

    @classmethod
    def build(
        cls,
        document_id: int,
        version: int,
        status: IndexingStatus,
        chunks_indexed: int,
        anonymized_items_count: int,
        model_name: str,
        indexed_at: datetime,
    ) -> Self:
        return cls(
            documentId=document_id,
            version=version,
            status=status,
            chunksIndexed=chunks_indexed,
            anonymizedItemsCount=anonymized_items_count,
            modelName=model_name,
            indexedAt=indexed_at,
        )


class KnowledgeDeleteResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    document_id: int = Field(alias="documentId")
    deleted_chunks: int = Field(alias="deletedChunks")
    status: DeleteStatus
