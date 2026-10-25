from app.models.knowledge import KnowledgeDocumentIndexRequest
from app.models.retrieval import RetrievalSearchRequest
from app.services.indexing_service import IndexingService
from app.services.retrieval_service import RetrievalService


def index_document(
    indexing_service: IndexingService,
    *,
    document_id: int,
    title: str,
    content: str,
    category: str,
    version: int = 1,
) -> None:
    indexing_service.index(
        KnowledgeDocumentIndexRequest(
            documentId=document_id,
            title=title,
            content=content,
            category=category,
            version=version,
            active=True,
        )
    )


def test_relevant_query_returns_ranked_chunks(
    indexing_service: IndexingService, retrieval_service: RetrievalService
) -> None:
    index_document(
        indexing_service,
        document_id=1,
        title="Processus de recrutement",
        content="Le processus de recrutement comporte un entretien et une décision.",
        category="RECRUITMENT_PROCEDURE",
    )
    index_document(
        indexing_service,
        document_id=2,
        title="Préparation entretien",
        content="Préparer un entretien candidat avec des exemples.",
        category="INTERVIEW_GUIDE",
    )

    response = retrieval_service.search(
        RetrievalSearchRequest(query="Comment se déroule un recrutement ?", topK=5)
    )

    assert response.total_results == 1
    assert response.results[0].document_id == 1
    assert response.results[0].category.value == "RECRUITMENT_PROCEDURE"
    assert response.results[0].score > 0.6
    assert response.model_name == "fake-multilingual-local-model"
    assert response.duration_ms >= 0


def test_results_are_ranked_by_similarity(
    indexing_service: IndexingService, retrieval_service: RetrievalService
) -> None:
    index_document(
        indexing_service,
        document_id=1,
        title="Recrutement",
        content="recrutement",
        category="RECRUITMENT_PROCEDURE",
    )
    index_document(
        indexing_service,
        document_id=2,
        title="Recrutement et entretien",
        content="recrutement entretien",
        category="INTERVIEW_GUIDE",
    )

    response = retrieval_service.search(
        RetrievalSearchRequest(query="recrutement", topK=5)
    )

    assert [result.document_id for result in response.results] == [1, 2]
    assert response.results[0].score > response.results[1].score


def test_category_filter(
    indexing_service: IndexingService, retrieval_service: RetrievalService
) -> None:
    index_document(
        indexing_service,
        document_id=1,
        title="Recrutement",
        content="recrutement candidat",
        category="RECRUITMENT_PROCEDURE",
    )
    index_document(
        indexing_service,
        document_id=2,
        title="Guide candidat",
        content="recrutement candidat",
        category="CANDIDATE_GUIDE",
    )

    response = retrieval_service.search(
        RetrievalSearchRequest(
            query="recrutement",
            topK=5,
            categories=["CANDIDATE_GUIDE"],
        )
    )

    assert response.total_results == 1
    assert response.results[0].document_id == 2
    assert response.results[0].category.value == "CANDIDATE_GUIDE"


def test_document_id_filter(
    indexing_service: IndexingService, retrieval_service: RetrievalService
) -> None:
    for document_id in (1, 2, 3):
        index_document(
            indexing_service,
            document_id=document_id,
            title=f"Document {document_id}",
            content="recrutement candidat",
            category="PLATFORM_HELP",
        )

    response = retrieval_service.search(
        RetrievalSearchRequest(query="recrutement", topK=5, documentIds=[2])
    )

    assert response.total_results == 1
    assert response.results[0].document_id == 2


def test_top_k_limits_results(
    indexing_service: IndexingService, retrieval_service: RetrievalService
) -> None:
    for document_id in range(1, 5):
        index_document(
            indexing_service,
            document_id=document_id,
            title=f"Document {document_id}",
            content="recrutement",
            category="FAQ",
        )

    response = retrieval_service.search(
        RetrievalSearchRequest(query="recrutement", topK=2)
    )

    assert response.total_results == 2
    assert len(response.results) == 2


def test_empty_index_returns_no_results(retrieval_service: RetrievalService) -> None:
    response = retrieval_service.search(
        RetrievalSearchRequest(query="Comment recruter ?", topK=5)
    )
    assert response.results == []
    assert response.total_results == 0


def test_low_relevance_results_are_filtered(
    indexing_service: IndexingService, retrieval_service: RetrievalService
) -> None:
    index_document(
        indexing_service,
        document_id=1,
        title="Recrutement",
        content="recrutement",
        category="FAQ",
    )

    response = retrieval_service.search(
        RetrievalSearchRequest(query="document sans rapport", topK=5)
    )

    assert response.results == []
    assert response.total_results == 0


def test_query_is_anonymized(
    indexing_service: IndexingService, retrieval_service: RetrievalService
) -> None:
    index_document(
        indexing_service,
        document_id=1,
        title="Recrutement",
        content="recrutement",
        category="FAQ",
    )

    response = retrieval_service.search(
        RetrievalSearchRequest(
            query="Contactez a@b.fr pour le recrutement", topK=5
        )
    )

    assert response.query == "Contactez a@b.fr pour le recrutement"
    assert "[EMAIL]" in response.sanitized_query
    assert "a@b.fr" not in response.sanitized_query


def test_retrieval_returns_chunks_not_generated_answers(
    indexing_service: IndexingService, retrieval_service: RetrievalService
) -> None:
    index_document(
        indexing_service,
        document_id=1,
        title="Recrutement",
        content="recrutement",
        category="FAQ",
    )

    response = retrieval_service.search(
        RetrievalSearchRequest(query="recrutement", topK=5)
    )
    payload = response.model_dump(by_alias=True)

    assert set(payload) == {
        "query",
        "sanitizedQuery",
        "results",
        "totalResults",
        "modelName",
        "durationMs",
    }
    assert "answer" not in payload
    assert "generatedAnswer" not in payload
    assert "response" not in payload
