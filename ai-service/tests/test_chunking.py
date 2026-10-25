import pytest

from app.models.knowledge import KnowledgeCategory
from app.services.chunking_service import ChunkingService


@pytest.fixture
def service() -> ChunkingService:
    return ChunkingService(chunk_size=200, chunk_overlap=40)


def build_chunks(service: ChunkingService, content: str, version: int = 1):
    return service.chunk_document(
        document_id=12,
        version=version,
        title="Guide candidat",
        content=content,
        category=KnowledgeCategory.CANDIDATE_GUIDE,
    )


def test_short_document_produces_one_chunk(service: ChunkingService) -> None:
    chunks = build_chunks(service, "Un texte court et suffisant.")
    assert len(chunks) == 1
    assert chunks[0].chunk_index == 0
    assert chunks[0].chunk_id == "document-12-v1-chunk-0"
    assert chunks[0].category == KnowledgeCategory.CANDIDATE_GUIDE
    assert chunks[0].title == "Guide candidat"


def test_long_document_is_split_with_respect_for_size(service: ChunkingService) -> None:
    content = "\n\n".join(
        "Le candidat prépare son dossier avec attention et vérifie chaque information."
        for _ in range(12)
    )
    chunks = build_chunks(service, content)
    assert len(chunks) > 1
    assert all(0 < len(chunk.content) <= 200 for chunk in chunks)
    assert [chunk.chunk_index for chunk in chunks] == list(range(len(chunks)))


def test_paragraphs_are_kept_in_order(service: ChunkingService) -> None:
    content = "Premier paragraphe.\n\nDeuxième paragraphe.\n\nTroisième paragraphe."
    chunks = build_chunks(service, content)
    assert len(chunks) == 1
    assert chunks[0].content.index("Premier") < chunks[0].content.index("Deuxième")
    assert chunks[0].content.index("Deuxième") < chunks[0].content.index("Troisième")


def test_overlap_exists_between_large_blocks(service: ChunkingService) -> None:
    content = "\n\n".join(
        f"Paragraphe {index} : le candidat vérifie les documents et prépare l'entretien."
        for index in range(20)
    )
    chunks = build_chunks(service, content)
    assert len(chunks) > 1
    assert any(
        chunks[index].content[:20] in chunks[index - 1].content
        for index in range(1, len(chunks))
    )


def test_accents_are_preserved(service: ChunkingService) -> None:
    content = "La préparation à l'entretien nécessite de réviser les épreuves."
    chunks = build_chunks(service, content)
    assert "préparation" in chunks[0].content
    assert "nécessite" in chunks[0].content


def test_chunking_is_deterministic(service: ChunkingService) -> None:
    content = "\n\n".join("Paragraphe répété avec des accents éàü." for _ in range(8))
    first = build_chunks(service, content)
    second = build_chunks(service, content)
    assert [chunk.content for chunk in first] == [chunk.content for chunk in second]
    assert [chunk.chunk_id for chunk in first] == [chunk.chunk_id for chunk in second]


def test_version_changes_identifiers(service: ChunkingService) -> None:
    chunks = build_chunks(service, "Contenu stable.", version=4)
    assert chunks[0].chunk_id == "document-12-v4-chunk-0"


def test_empty_content_is_rejected(service: ChunkingService) -> None:
    with pytest.raises(ValueError):
        build_chunks(service, " \n\n ")


def test_oversized_single_word_is_split(service: ChunkingService) -> None:
    chunks = build_chunks(service, "a" * 450)
    assert len(chunks) >= 3
    assert all(len(chunk.content) <= 200 for chunk in chunks)
