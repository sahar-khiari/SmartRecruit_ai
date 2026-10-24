import secrets
from typing import Annotated

from fastapi import Depends, HTTPException, Security, status
from fastapi.security import APIKeyHeader

from app.core.config import Settings, get_settings

API_KEY_HEADER_NAME = "X-AI-Service-Key"
api_key_header = APIKeyHeader(name=API_KEY_HEADER_NAME, auto_error=False)


def require_internal_api_key(
    provided_key: Annotated[str | None, Security(api_key_header)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> None:
    """Protège les endpoints internes avec une comparaison à temps constant.

    /health reste public pour permettre la supervision locale. Les endpoints
    d'indexation, de suppression et de recherche exigent la clé interne.
    """
    if not provided_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Clé interne manquante",
        )

    if not secrets.compare_digest(provided_key, settings.api_key_value):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Clé interne invalide",
        )
