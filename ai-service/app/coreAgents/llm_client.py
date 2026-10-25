"""
Client LLM — Module 4 TechMatch.
Auteur : Aziz

Gere les appels au LLM avec :
  - Timeout configurable
  - Verification cle absente
  - Gestion quota depasse (429)
  - Validation JSON de la reponse

Compatibilite Groq : on n'utilise PAS response_format (cause des erreurs 404
sur certains modeles). Le JSON est demande dans le prompt systeme directement.
"""
import json
import re
from typing import Optional

import httpx

from app.config import settings


class LLMError(Exception):
    """Erreur propre pour tous les problemes LLM - affichee a l'utilisateur."""


def _appeler_llm(messages: list[dict], json_schema: Optional[dict] = None) -> dict:
    """
    Appelle le LLM et retourne un dict (JSON valide).

    Leve LLMError si :
      - La cle API est absente
      - Le timeout est depasse
      - Le quota est epuise (429)
      - La reponse n'est pas un JSON valide
    """
    # Regle : verifier la cle avant d'appeler
    if not settings.LLM_API_KEY or settings.LLM_API_KEY == "ta_cle_groq_ici":
        raise LLMError(
            "Cle API LLM manquante. "
            "Copie .env.example vers .env et remplis LLM_API_KEY."
        )

    # Construire les messages avec instruction JSON dans le system prompt
    # NOTE : on n'utilise PAS response_format car Groq renvoie 404 sur certains modeles
    messages_envoyes = list(messages)

    # Injecter l'instruction JSON en premier message systeme
    instruction_json = "Tu dois repondre UNIQUEMENT avec un objet JSON valide, sans texte avant ou apres, sans markdown, sans ```json."
    if json_schema:
        schema_str = json.dumps(json_schema, ensure_ascii=False, separators=(",", ":"))
        instruction_json += f" Le JSON doit respecter ce schema : {schema_str}"

    # Ajouter ou prependre le message systeme JSON
    if messages_envoyes and messages_envoyes[0].get("role") == "system":
        messages_envoyes[0] = {
            "role": "system",
            "content": messages_envoyes[0]["content"] + "\n\n" + instruction_json,
        }
    else:
        messages_envoyes.insert(0, {"role": "system", "content": instruction_json})

    payload: dict = {
        "model": settings.LLM_MODEL,
        "messages": messages_envoyes,
        "temperature": 0.2,
        "max_tokens": 4000,
        # PAS de response_format ici - incompatible avec certains modeles Groq (cause 404)
    }

    url = f"{settings.LLM_BASE_URL.rstrip('/')}/v1/chat/completions"

    try:
        response = httpx.post(
            url,
            json=payload,
            headers={"Authorization": f"Bearer {settings.LLM_API_KEY}"},
            timeout=settings.LLM_TIMEOUT,
        )

        if response.status_code == 429:
            raise LLMError(
                "Quota LLM depasse. Attends quelques secondes et reessaie."
            )

        if response.status_code == 401:
            raise LLMError(
                "Cle API invalide (erreur 401). Verifie LLM_API_KEY dans ton .env."
            )

        response.raise_for_status()
        contenu = response.json()["choices"][0]["message"]["content"]

    except httpx.TimeoutException:
        raise LLMError(
            f"Le LLM n'a pas repondu en {settings.LLM_TIMEOUT}s. Reessaie."
        )
    except httpx.HTTPStatusError as exc:
        corps = ""
        try:
            corps = exc.response.json().get("error", {}).get("message", "")
        except Exception:
            pass
        raise LLMError(
            f"Erreur du service LLM (HTTP {exc.response.status_code}). "
            f"{corps or 'Verifie ta cle API et le nom du modele dans .env.'}"
        ) from exc
    except httpx.HTTPError as exc:
        raise LLMError(f"Impossible de joindre le LLM : {exc}") from exc

    # Nettoyer la reponse : parfois le LLM ajoute ```json ... ```
    contenu = contenu.strip()
    if contenu.startswith("```"):
        contenu = re.sub(r"^```(?:json)?\s*", "", contenu)
        contenu = re.sub(r"\s*```$", "", contenu)

    # Valider que c'est bien du JSON
    try:
        return json.loads(contenu)
    except json.JSONDecodeError as exc:
        raise LLMError(
            "Le LLM n'a pas retourne un JSON valide. Reessaie."
        ) from exc
