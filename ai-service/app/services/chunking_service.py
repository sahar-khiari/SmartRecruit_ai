import re
from dataclasses import dataclass

from app.models.knowledge import KnowledgeCategory
from app.services.text_cleaning_service import TextCleaningService
from app.utils.identifiers import build_chunk_id


@dataclass(frozen=True)
class DocumentChunk:
    document_id: int
    chunk_index: int
    content: str
    title: str
    category: KnowledgeCategory
    version: int

    @property
    def chunk_id(self) -> str:
        return build_chunk_id(
            document_id=self.document_id,
            version=self.version,
            chunk_index=self.chunk_index,
        )


class ChunkingService:
    """Découpe un document en passages cohérents et déterministes.

    Les paragraphes sont accumulés jusqu'à CHUNK_SIZE. Les paragraphes trop
    longs sont découpés aux frontières de phrases ou de mots. Un chevauchement
    de CHUNK_OVERLAP caractères est réutilisé entre deux chunks consécutifs.
    """

    _PARAGRAPH_SEPARATOR = re.compile(r"\n\s*\n+")
    _SENTENCE_BOUNDARY = re.compile(r"(?<=[.!?…])\s+")

    def __init__(
        self,
        chunk_size: int = 700,
        chunk_overlap: int = 100,
        cleaning_service: TextCleaningService | None = None,
    ) -> None:
        if chunk_size < 50:
            raise ValueError("chunk_size doit être supérieur ou égal à 50")
        if chunk_overlap < 0 or chunk_overlap >= chunk_size:
            raise ValueError("chunk_overlap doit être strictement inférieur à chunk_size")
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap
        self._cleaning = cleaning_service or TextCleaningService()

    def chunk_document(
        self,
        *,
        document_id: int,
        version: int,
        title: str,
        content: str,
        category: KnowledgeCategory,
    ) -> list[DocumentChunk]:
        cleaned = self._cleaning.clean(content)
        blocks = self._split_into_blocks(cleaned)
        chunks = self._pack_blocks(blocks)
        return [
            DocumentChunk(
                document_id=document_id,
                chunk_index=index,
                content=chunk,
                title=title,
                category=category,
                version=version,
            )
            for index, chunk in enumerate(chunks)
            if chunk.strip()
        ]

    def _split_into_blocks(self, text: str) -> list[str]:
        blocks: list[str] = []
        for paragraph in self._PARAGRAPH_SEPARATOR.split(text):
            paragraph = paragraph.strip()
            if not paragraph:
                continue
            if len(paragraph) <= self.chunk_size:
                blocks.append(paragraph)
            else:
                blocks.extend(self._split_oversized_block(paragraph))
        return blocks

    def _split_oversized_block(self, block: str) -> list[str]:
        sentences = [part.strip() for part in self._SENTENCE_BOUNDARY.split(block) if part.strip()]
        blocks: list[str] = []
        current = ""

        for sentence in sentences:
            if len(sentence) > self.chunk_size:
                if current:
                    blocks.append(current)
                    current = ""
                blocks.extend(self._split_by_words(sentence))
                continue

            candidate = sentence if not current else f"{current} {sentence}"
            if len(candidate) <= self.chunk_size:
                current = candidate
            else:
                if current:
                    blocks.append(current)
                current = sentence

        if current:
            blocks.append(current)
        return blocks

    def _split_by_words(self, text: str) -> list[str]:
        words = text.split()
        blocks: list[str] = []
        start = 0
        while start < len(words):
            end = start
            length = 0
            while end < len(words):
                additional = len(words[end]) + (1 if length else 0)
                if length + additional > self.chunk_size:
                    break
                length += additional
                end += 1

            if end == start:
                # Un « mot » unique dépasse la taille cible : découpe brute.
                raw = words[start]
                blocks.extend(
                    raw[offset : offset + self.chunk_size]
                    for offset in range(0, len(raw), self.chunk_size)
                )
                start += 1
                continue

            blocks.append(" ".join(words[start:end]))
            if end >= len(words):
                break
            overlap_words: list[str] = []
            overlap_length = 0
            for word in reversed(words[start:end]):
                if overlap_length + len(word) + 1 > self.chunk_overlap:
                    break
                overlap_words.insert(0, word)
                overlap_length += len(word) + 1
            next_start = end - len(overlap_words)
            start = max(next_start, start + 1)
        return blocks

    def _pack_blocks(self, blocks: list[str]) -> list[str]:
        chunks: list[str] = []
        current = ""

        for block in blocks:
            if not current:
                current = block
                continue

            candidate = f"{current}\n\n{block}"
            if len(candidate) <= self.chunk_size:
                current = candidate
                continue

            chunks.append(current)
            overlap = self._overlap_tail(current)
            candidate_with_overlap = (
                f"{overlap}\n\n{block}" if overlap else block
            )
            current = (
                candidate_with_overlap
                if len(candidate_with_overlap) <= self.chunk_size
                else block
            )

        if current:
            chunks.append(current)
        return chunks

    def _overlap_tail(self, text: str) -> str:
        if self.chunk_overlap <= 0 or len(text) <= self.chunk_overlap:
            return ""
        tail = text[-self.chunk_overlap :]
        first_space = re.search(r"\s", tail)
        if first_space:
            tail = tail[first_space.end() :]
        tail = tail.strip()
        return "" if tail == text.strip() else tail
