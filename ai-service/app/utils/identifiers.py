def build_chunk_id(*, document_id: int, version: int, chunk_index: int) -> str:
    """Identifiant déterministe et idempotent d'un chunk."""
    if document_id <= 0:
        raise ValueError("document_id doit être positif")
    if version < 0:
        raise ValueError("version doit être positive ou nulle")
    if chunk_index < 0:
        raise ValueError("chunk_index doit être positif ou nul")
    return f"document-{document_id}-v{version}-chunk-{chunk_index}"
