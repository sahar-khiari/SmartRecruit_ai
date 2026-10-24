from typing import Annotated

from fastapi import APIRouter, Depends, Path

from app.core.security import require_internal_api_key
from app.models.knowledge import (
    KnowledgeDeleteResponse,
    KnowledgeDocumentIndexRequest,
    KnowledgeDocumentIndexResponse,
    DeleteStatus,
)
from app.services.indexing_service import IndexingService, get_indexing_service
from app.services.vector_store_service import VectorStoreService, get_vector_store_service

router = APIRouter(
    prefix="/api/v1/knowledge",
    tags=["Indexation interne"],
    dependencies=[Depends(require_internal_api_key)],
)


@router.post(
    "/index",
    response_model=KnowledgeDocumentIndexResponse,
    summary="Indexer ou réindexer un document RH",
    description=(
        "Endpoint interne appelé exclusivement par Spring Boot. Le document provient "
        "de MariaDB. L'opération est idempotente : les anciens chunks du document sont "
        "remplacés. Un document inactif est retiré de l'index. Aucun LLM n'est utilisé."
    ),
)
def index_document(
    request: KnowledgeDocumentIndexRequest,
    service: Annotated[IndexingService, Depends(get_indexing_service)],
) -> KnowledgeDocumentIndexResponse:
    return service.index(request)


@router.delete(
    "/{document_id}",
    response_model=KnowledgeDeleteResponse,
    summary="Supprimer les chunks d'un document",
    description=(
        "Supprime tous les chunks vectoriels du document. Idempotent si le document "
        "n'est déjà plus indexé. MariaDB reste la source de vérité."
    ),
)
def delete_document(
    document_id: Annotated[int, Path(gt=0)],
    vector_store: Annotated[VectorStoreService, Depends(get_vector_store_service)],
) -> KnowledgeDeleteResponse:
    deleted = vector_store.delete_document(document_id)
    return KnowledgeDeleteResponse(
        documentId=document_id,
        deletedChunks=deleted,
        status=DeleteStatus.DELETED,
    )
