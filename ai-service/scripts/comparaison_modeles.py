"""
Comparaison de modeles LLM — Module 4 TechMatch.
Auteur : Aziz

Ce script repond a la question : "Ton module consomme juste une API ?"
→ Non. On compare 2 modeles sur le meme prompt et on mesure :
  - La latence (temps de reponse)
  - La qualite (nombre de questions generees correctement)
  - Le cout estime (en tokens)

Resultats sauvegardes dans docs/comparaison_modeles.json
"""
import httpx, json, time, os
from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("LLM_API_KEY", "")
BASE_URL = os.getenv("LLM_BASE_URL", "https://api.groq.com/openai")
URL = f"{BASE_URL.rstrip('/')}/v1/chat/completions"

MODELES = {
    "gpt-oss-20b":  "openai/gpt-oss-20b",   # Rapide, moins puissant
    "gpt-oss-120b": "openai/gpt-oss-120b",   # Plus lent, plus puissant
}

PROMPT_SYSTEME = (
    "Tu es un expert en evaluation technique. "
    "Reponds UNIQUEMENT avec un objet JSON valide, sans texte avant ou apres."
)

PROMPT_UTILISATEUR = """Genere un test technique pour un poste de Developpeur Backend Python.
Competences a evaluer : Docker (debutant), FastAPI (intermediaire).

Retourne un JSON avec exactement cette structure :
{
  "titre": "...",
  "nb_questions": 5,
  "questions": [
    {"numero": 1, "type": "qcm", "enonce": "...", "competence": "..."},
    {"numero": 2, "type": "qcm", "enonce": "...", "competence": "..."},
    {"numero": 3, "type": "qcm", "enonce": "...", "competence": "..."},
    {"numero": 4, "type": "ouverte", "enonce": "...", "competence": "..."},
    {"numero": 5, "type": "ouverte", "enonce": "...", "competence": "..."}
  ]
}"""


def appeler_modele(nom_modele: str, id_modele: str) -> dict:
    """Appelle un modele et mesure les metriques."""
    print(f"\nTest du modele : {nom_modele} ({id_modele})")
    print("-" * 50)

    debut = time.perf_counter()
    try:
        r = httpx.post(
            URL,
            json={
                "model": id_modele,
                "messages": [
                    {"role": "system", "content": PROMPT_SYSTEME},
                    {"role": "user",   "content": PROMPT_UTILISATEUR},
                ],
                "temperature": 0.2,
                "max_tokens": 1500,
            },
            headers={"Authorization": f"Bearer {API_KEY}"},
            timeout=120,
        )
        latence_ms = int((time.perf_counter() - debut) * 1000)

        if r.status_code != 200:
            return {
                "modele": nom_modele,
                "erreur": f"HTTP {r.status_code}",
                "latence_ms": latence_ms,
            }

        body = r.json()
        contenu = body["choices"][0]["message"]["content"].strip()

        # Nettoyer les backticks si present
        if contenu.startswith("```"):
            import re
            contenu = re.sub(r"^```(?:json)?\s*", "", contenu)
            contenu = re.sub(r"\s*```$", "", contenu)

        # Tokens utilises
        usage = body.get("usage", {})
        tokens_prompt     = usage.get("prompt_tokens", 0)
        tokens_completion = usage.get("completion_tokens", 0)
        tokens_total      = usage.get("total_tokens", 0)

        # Valider JSON
        try:
            data = json.loads(contenu)
            questions = data.get("questions", [])
            nb_valides = sum(
                1 for q in questions
                if q.get("enonce") and q.get("type") and q.get("competence")
            )
            json_valide = True
        except Exception:
            data = {}
            questions = []
            nb_valides = 0
            json_valide = False

        print(f"  Latence          : {latence_ms} ms")
        print(f"  Tokens prompt    : {tokens_prompt}")
        print(f"  Tokens completion: {tokens_completion}")
        print(f"  Tokens total     : {tokens_total}")
        print(f"  JSON valide      : {json_valide}")
        print(f"  Questions OK     : {nb_valides}/5")
        if questions:
            print(f"  Exemple Q1       : {questions[0].get('enonce', '')[:60]}...")

        return {
            "modele": nom_modele,
            "id_modele": id_modele,
            "latence_ms": latence_ms,
            "tokens_prompt": tokens_prompt,
            "tokens_completion": tokens_completion,
            "tokens_total": tokens_total,
            "json_valide": json_valide,
            "questions_valides": nb_valides,
            "questions_total": 5,
            "taux_conformite_pct": round(nb_valides / 5 * 100),
        }

    except httpx.TimeoutException:
        latence_ms = int((time.perf_counter() - debut) * 1000)
        print(f"  TIMEOUT apres {latence_ms} ms")
        return {"modele": nom_modele, "erreur": "timeout", "latence_ms": latence_ms}
    except Exception as exc:
        return {"modele": nom_modele, "erreur": str(exc)}


def main():
    print("=" * 60)
    print("COMPARAISON DE MODELES LLM — Module 4 TechMatch")
    print("Auteur : Aziz | Cours : IA for SE, ESPRIT 2026-2027")
    print("=" * 60)
    print(f"Prompt : {len(PROMPT_UTILISATEUR)} caracteres")
    print(f"Modeles testes : {list(MODELES.keys())}")

    resultats = []
    for nom, id_mod in MODELES.items():
        res = appeler_modele(nom, id_mod)
        resultats.append(res)

    # Tableau comparatif
    print("\n" + "=" * 60)
    print("TABLEAU COMPARATIF")
    print("=" * 60)
    print(f"{'Modele':<16} {'Latence':>10} {'Tokens':>8} {'JSON OK':>8} {'Q. valides':>12} {'Conformite':>12}")
    print("-" * 70)
    for r in resultats:
        if "erreur" in r:
            print(f"{r['modele']:<16} {'ERREUR: '+r['erreur']}")
        else:
            print(
                f"{r['modele']:<16}"
                f"{str(r['latence_ms'])+' ms':>10}"
                f"{r['tokens_total']:>8}"
                f"{'Oui' if r['json_valide'] else 'Non':>8}"
                f"{str(r['questions_valides'])+'/5':>12}"
                f"{str(r['taux_conformite_pct'])+'%':>12}"
            )

    # Conclusion
    valides = [r for r in resultats if "erreur" not in r and r.get("json_valide")]
    if len(valides) >= 2:
        rapide = min(valides, key=lambda x: x["latence_ms"])
        precis = max(valides, key=lambda x: x["questions_valides"])
        print(f"\nModele le + rapide  : {rapide['modele']} ({rapide['latence_ms']} ms)")
        print(f"Modele le + conforme: {precis['modele']} ({precis['questions_valides']}/5 questions OK)")
        print(f"\nConclusion : utiliser '{precis['modele']}' en production, '{rapide['modele']}' en dev rapide.")

    # Sauvegarder le rapport
    os.makedirs("docs", exist_ok=True)
    rapport = {
        "date": time.strftime("%Y-%m-%d %H:%M"),
        "auteur": "Aziz",
        "module": "Module 4 - Tests techniques",
        "prompt_utilisateur_chars": len(PROMPT_UTILISATEUR),
        "resultats": resultats,
    }
    with open("docs/comparaison_modeles.json", "w", encoding="utf-8") as f:
        json.dump(rapport, f, ensure_ascii=False, indent=2)
    print("\nRapport sauvegarde dans docs/comparaison_modeles.json")


if __name__ == "__main__":
    main()
