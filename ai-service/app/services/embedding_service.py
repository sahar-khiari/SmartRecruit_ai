import logging
import threading
from functools import lru_cache

from app.core.config import Settings, get_settings

logger = logging.getLogger(__name__)


class EmbeddingService:
    """Embeddings locaux via sentence-transformers, chargés paresseusement.

    Le premier lancement peut télécharger le modèle depuis Hugging Face.
    Les lancements suivants réutilisent le cache local de Hugging Face.
    Aucune API distante d'embeddings n'est utilisée.
    """

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._model = None
        self._lock = threading.Lock()

    @property
    def model_name(self) -> str:
        return self._settings.embedding_model_name

    def ensure_loaded(self) -> bool:
        """Tente d'initialiser le modèle sans exposer de détail sensible."""
        try:
            self._get_model()
            return True
        except Exception as exc:  # noqa: BLE001 - diagnostic contrôlé
            logger.warning(
                "Modèle d'embeddings indisponible (%s). Vérifiez le cache local et Internet.",
                exc.__class__.__name__,
            )
            return False

    def _get_model(self):
        if self._model is not None:
            return self._model

        with self._lock:
            if self._model is None:
                from sentence_transformers import SentenceTransformer

                self._model = SentenceTransformer(
                    self._settings.embedding_model_name,
                    device=self._settings.embedding_device,
                )
        return self._model

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        model = self._get_model()
        embeddings = model.encode(
            texts,
            batch_size=32,
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        )
        return embeddings.tolist()

    def embed_query(self, text: str) -> list[float]:
        embeddings = self.embed_documents([text])
        if not embeddings:
            raise ValueError("Impossible de générer l'embedding de la requête")
        return embeddings[0]


@lru_cache
def get_embedding_service() -> EmbeddingService:
    return EmbeddingService(get_settings())
