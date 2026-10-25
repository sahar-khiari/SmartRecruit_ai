"""
Service d'embeddings pour le module matching (Layer 1).

Chargement paresseux du modèle sentence-transformers.
Si le modèle ne peut pas être chargé, le service retourne available=False
et Spring bascule silencieusement sur la Layer 0.

Ce service est INDÉPENDANT du service d'embedding du Module 1 (RAG RH).
Il utilise le même modèle mais un état séparé pour éviter tout couplage.
"""
from __future__ import annotations

import logging
from typing import List, Optional, Tuple

import numpy as np

logger = logging.getLogger(__name__)

# État global du modèle (chargement paresseux)
_model = None
_model_name: Optional[str] = None
_load_error: Optional[str] = None


def _load_model(model_name: str) -> bool:
    """Charge le modèle sentence-transformers de façon paresseuse."""
    global _model, _model_name, _load_error
    if _model is not None and _model_name == model_name:
        return True
    if _load_error and _model_name == model_name:
        return False  # Ne pas retenter si déjà échoué

    try:
        from sentence_transformers import SentenceTransformer
        logger.info("[Matching] Chargement du modèle d'embedding : %s", model_name)
        _model = SentenceTransformer(model_name)
        _model_name = model_name
        _load_error = None
        logger.info("[Matching] Modèle d'embedding chargé avec succès.")
        return True
    except Exception as exc:
        _load_error = str(exc)
        _model_name = model_name
        logger.warning("[Matching] Impossible de charger le modèle d'embedding : %s", exc)
        return False


def embed_texts(texts: List[str], model_name: str) -> Tuple[bool, Optional[np.ndarray]]:
    """
    Encode une liste de textes en vecteurs d'embeddings.

    Returns:
        (True, embeddings_array) si succès
        (False, None) si modèle indisponible
    """
    if not _load_model(model_name):
        return False, None
    try:
        embeddings = _model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
        return True, embeddings
    except Exception as exc:
        logger.warning("[Matching] Erreur embedding : %s", exc)
        return False, None


def cosine_similarity_batch(query_vec: np.ndarray, candidate_vecs: np.ndarray) -> np.ndarray:
    """
    Calcule la similarité cosinus entre un vecteur requête et N vecteurs candidats.
    Les vecteurs doivent être normalisés (norme = 1).
    Retourne un tableau de scores dans [0, 1].
    """
    # Les vecteurs sont déjà normalisés par SentenceTransformer (normalize_embeddings=True)
    scores = np.dot(candidate_vecs, query_vec)
    return np.clip(scores, 0.0, 1.0)
