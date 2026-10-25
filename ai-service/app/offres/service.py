"""Logique IA du Module 2 : description, compétences, mots-clés.

Chaque fonctionnalité : anonymisation -> cache -> appel Cohere -> validation Pydantic
(avec nouvel essai si la sortie est invalide) -> réponse.
"""
from __future__ import annotations

import hashlib
import json
import logging
import math
import re
from collections import Counter, OrderedDict
from typing import Any, TypeVar

from pydantic import BaseModel, ValidationError

from app.offres import prompts
from app.offres.cohere_client import CohereClient, CohereInvalidOutputError
from app.offres.config import OffresSettings
from app.offres.referentiel_competences import COMPETENCES
from app.offres.schemas import (
    CompetencesRequest,
    CompetencesResponse,
    CompetenceSuggeree,
    DescriptionRequest,
    DescriptionResponse,
    LlmCompetences,
    LlmDescription,
    LlmMotsCles,
    MotCle,
    MotsClesRequest,
    MotsClesResponse,
)
from app.services.anonymization_service import AnonymizationService

logger = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseModel)

NON_PRECISE = "non précisé"

# Mots vides FR/EN ignorés par la méthode de référence (sans IA) des mots-clés.
_STOPWORDS = set("""
a à au aux avec ce ces cette dans de des du elle en et être eux il ils je la le les leur lui ma mais me
même mes moi mon ne nos notre nous on ou où par pas pour qu que qui sa se ses son sur ta te tes toi ton tu
un une vos votre vous y d l s n c j m t qu est sont sera vous nous afin ainsi aussi plus très tout tous
toute toutes chez entre selon dont comme être avoir fait faire bien bon bonne nos vos leurs sein
the and or of to in for with on at by an be is are as from this that you your we our will can
poste profil mission missions candidat candidate recherchons rejoindre équipe ans année années
""".split())


class OffresIaService:
    def __init__(self, settings: OffresSettings, client: CohereClient,
                 anonymizer: AnonymizationService | None = None) -> None:
        self._settings = settings
        self._client = client
        self._anonymizer = anonymizer or AnonymizationService()
        self._cache: OrderedDict[str, Any] = OrderedDict()
        self._skill_vectors: list[list[float]] | None = None

    # ------------------------------------------------------------- description

    def generer_description(self, req: DescriptionRequest) -> DescriptionResponse:
        champs = {
            "titre_poste": self._anon(req.titre_poste),
            "niveau_requis": self._anon(req.niveau_requis),
            "type_contrat": self._anon(req.type_contrat),
            "localisation": self._anon(req.localisation),
            "competences": self._anon(req.competences),
            "notes": self._anon(req.notes),
        }
        cle = self._cle_cache("description", champs)
        en_cache = self._cache_get(cle)
        if en_cache is not None:
            return en_cache.model_copy(update={"depuis_cache": True})

        sortie = self._chat_valide(
            prompts.DESCRIPTION_SYSTEM,
            prompts.DESCRIPTION_USER.format(**champs),
            LlmDescription,
            temperature=0.4,
        )
        resultat = DescriptionResponse(
            description=self._composer_description(sortie),
            accroche=sortie.accroche,
            missions=sortie.missions,
            profil=sortie.profil,
            avantages=sortie.avantages,
            modele=self._settings.cohere_chat_model,
            version_prompt=prompts.PROMPT_VERSION,
            depuis_cache=False,
        )
        self._cache_put(cle, resultat)
        return resultat

    # ------------------------------------------------------------- compétences

    def suggerer_competences(self, req: CompetencesRequest) -> CompetencesResponse:
        titre = self._anon(req.titre_poste)
        description = self._anon(req.description)
        niveau = self._anon(req.niveau_requis)
        cle = self._cle_cache("competences", [titre, description, niveau])
        en_cache = self._cache_get(cle)
        if en_cache is not None:
            return en_cache.model_copy(update={"depuis_cache": True})

        # Étape 1 : pré-sélection rapide par similarité cosinus (Cohere Embed).
        texte_offre = f"{titre}. {niveau}. {description}"[:4000]
        vecteur_offre = self._client.embed([texte_offre], input_type="search_query")[0]
        scores = [
            (idx, _cosinus(vecteur_offre, vecteur))
            for idx, vecteur in enumerate(self._vecteurs_competences())
        ]
        scores.sort(key=lambda item: item[1], reverse=True)
        top = scores[:20]
        score_par_nom = {COMPETENCES[idx][0].lower(): (idx, score) for idx, score in top}

        # Étape 2 : le LLM choisit les compétences réellement requises parmi les candidates.
        candidates = ", ".join(COMPETENCES[idx][0] for idx, _ in top)
        methode = "embedding+llm"
        try:
            sortie = self._chat_valide(
                prompts.COMPETENCES_SYSTEM,
                prompts.COMPETENCES_USER.format(titre_poste=titre, niveau_requis=niveau,
                                                description=description, candidates=candidates),
                LlmCompetences,
                temperature=0.0,
            )
            suggestions = []
            for nom in _dedoublonner(sortie.competences):
                trouve = score_par_nom.get(nom.lower())
                if trouve is not None:
                    idx, score = trouve
                    suggestions.append(CompetenceSuggeree(
                        nom=COMPETENCES[idx][0], categorie=COMPETENCES[idx][1],
                        score=round(_borner(score), 3), source="embedding+llm"))
                else:
                    suggestions.append(CompetenceSuggeree(
                        nom=nom[:60], categorie="Autre", score=0.5, source="llm"))
        except CohereInvalidOutputError:
            # Repli contrôlé : on garde la pré-sélection par embeddings.
            logger.warning("Compétences : sortie LLM invalide, repli sur les embeddings")
            methode = "embedding"
            suggestions = [
                CompetenceSuggeree(nom=COMPETENCES[idx][0], categorie=COMPETENCES[idx][1],
                                   score=round(_borner(score), 3), source="embedding")
                for idx, score in top[:8]
            ]

        resultat = CompetencesResponse(
            competences=suggestions,
            methode=methode,
            modele=f"{self._settings.cohere_embed_model} + {self._settings.cohere_chat_model}",
            depuis_cache=False,
        )
        self._cache_put(cle, resultat)
        return resultat

    # --------------------------------------------------------------- mots-clés

    def detecter_mots_cles(self, req: MotsClesRequest) -> MotsClesResponse:
        titre = self._anon(req.titre_poste)
        description = self._anon(req.description)
        cle = self._cle_cache("mots_cles", [titre, description])
        en_cache = self._cache_get(cle)
        if en_cache is not None:
            return en_cache.model_copy(update={"depuis_cache": True})

        sortie = self._chat_valide(
            prompts.MOTS_CLES_SYSTEM,
            prompts.MOTS_CLES_USER.format(titre_poste=titre, description=description),
            LlmMotsCles,
            temperature=0.0,
        )
        vus: set[str] = set()
        mots: list[MotCle] = []
        for item in sorted(sortie.mots_cles, key=lambda m: m.importance, reverse=True):
            if item.mot.lower() not in vus:
                vus.add(item.mot.lower())
                mots.append(MotCle(mot=item.mot.strip(), importance=item.importance))

        resultat = MotsClesResponse(
            mots_cles=mots,
            mots_cles_baseline=mots_cles_baseline(f"{titre} {description}"),
            modele=self._settings.cohere_chat_model,
            depuis_cache=False,
        )
        self._cache_put(cle, resultat)
        return resultat

    # ----------------------------------------------------------------- helpers

    def _chat_valide(self, system: str, user: str, modele: type[T], temperature: float) -> T:
        """Appelle Cohere et valide la sortie ; nouvel essai si le JSON ne respecte pas le schéma."""
        essais = 1 + self._settings.cohere_max_retries
        for essai in range(1, essais + 1):
            try:
                brut = self._client.chat_json(system, user, temperature=temperature)
                return modele.model_validate(brut)
            except (ValidationError, CohereInvalidOutputError) as exc:
                logger.warning("Sortie Cohere invalide (%s), essai %d/%d", type(exc).__name__, essai, essais)
        raise CohereInvalidOutputError("La réponse de l'IA ne respecte pas le format attendu")

    def _vecteurs_competences(self) -> list[list[float]]:
        # Calculés une seule fois puis gardés en mémoire (économie d'appels).
        if self._skill_vectors is None:
            textes = [f"{nom} ({categorie})" for nom, categorie in COMPETENCES]
            self._skill_vectors = self._client.embed(textes, input_type="search_document")
        return self._skill_vectors

    def _anon(self, value: str | None) -> str:
        if value is None or not value.strip():
            return NON_PRECISE
        return self._anonymizer.anonymize(value).text or NON_PRECISE

    @staticmethod
    def _composer_description(sortie: LlmDescription) -> str:
        lignes = [sortie.accroche, "", "Missions :"]
        lignes += [f"- {m}" for m in sortie.missions]
        lignes += ["", "Profil recherché :"]
        lignes += [f"- {p}" for p in sortie.profil]
        if sortie.avantages:
            lignes += ["", "Avantages :"]
            lignes += [f"- {a}" for a in sortie.avantages]
        return "\n".join(lignes)

    @staticmethod
    def _cle_cache(prefixe: str, contenu: Any) -> str:
        brut = json.dumps(contenu, ensure_ascii=False, sort_keys=True)
        return prefixe + ":" + hashlib.sha256(brut.encode("utf-8")).hexdigest()

    def _cache_get(self, cle: str) -> Any:
        if cle in self._cache:
            self._cache.move_to_end(cle)
            return self._cache[cle]
        return None

    def _cache_put(self, cle: str, valeur: Any) -> None:
        if self._settings.offres_cache_size == 0:
            return
        self._cache[cle] = valeur
        self._cache.move_to_end(cle)
        while len(self._cache) > self._settings.offres_cache_size:
            self._cache.popitem(last=False)


# --------------------------------------------------------------------- fonctions

def mots_cles_baseline(texte: str, limite: int = 10) -> list[str]:
    """Méthode de référence SANS IA (fréquence des mots) pour comparer avec Cohere dans le rapport."""
    mots = re.findall(r"[a-zA-ZÀ-ÿ0-9+#.]{3,}", texte.lower())
    mots = [m.strip(".") for m in mots if m.strip(".") not in _STOPWORDS and not m.isdigit()]
    return [mot for mot, _ in Counter(mots).most_common(limite)]


def _cosinus(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def _borner(score: float) -> float:
    return max(0.0, min(1.0, score))


def _dedoublonner(valeurs: list[str]) -> list[str]:
    vus: set[str] = set()
    resultat = []
    for v in valeurs:
        v = v.strip()
        if v and v.lower() not in vus:
            vus.add(v.lower())
            resultat.append(v)
    return resultat


_service: OffresIaService | None = None


def get_offres_service() -> OffresIaService:
    """Singleton (le cache et les vecteurs du référentiel restent en mémoire entre les requêtes)."""
    global _service
    if _service is None:
        from app.offres.config import get_offres_settings
        settings = get_offres_settings()
        _service = OffresIaService(settings, CohereClient(settings))
    return _service
