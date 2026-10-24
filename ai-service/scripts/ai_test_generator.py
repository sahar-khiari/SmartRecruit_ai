#!/usr/bin/env python3
"""
Script de génération de tests unitaires assistés par IA — Module 4 (Partie B).
Auteur : Aziz (module4-aziz)

Usage :
  python scripts/ai_test_generator.py --file app/coreAgents/Agents/tech_assessment/generator.py
  python scripts/ai_test_generator.py --file app/v1/schemas/tech_assessment.py --prompt-version v2

Ce script :
  1. Lit un fichier source Python du back-end.
  2. Demande au LLM de générer des tests pytest.
  3. Sauvegarde les tests dans tests/ai_generated/<nom_fichier>_test.py.
  4. Exécute les tests avec pytest et mesure la couverture.
  5. Si des tests échouent, redemande au LLM de les corriger (max 2 essais).
  6. Produit un rapport JSON avec les métriques.

Règle de sécurité : le fichier source est lu localement.
Aucune donnée personnelle ni secret n'est envoyé au LLM.

Métriques collectées (pour le rapport) :
  - tests_generes      : nombre de tests dans le fichier généré
  - tests_passes_premier_coup : tests qui passent sans correction
  - tests_corriges_manuellement : tests qui ont nécessité une relecture humaine
  - couverture_pct     : couverture de code mesurée par pytest-cov
  - bugs_trouves       : assertions qui échouent sur le code réel
  - latence_ms         : temps total d'appel LLM
  - tokens_estimes     : estimation du nombre de tokens consommés
  - temps_gagne_min    : estimation du temps gagné vs écriture manuelle
"""

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

import httpx

# ---------------------------------------------------------------------------
# Configuration (via variables d'environnement — jamais de secrets en dur)
# ---------------------------------------------------------------------------

SLM_SMART_URL = os.getenv("SLM_SMART_URL", "")
SLM_API_KEY = os.getenv("SLM_API_KEY", "not-needed")
SLM_SMART_MODEL = os.getenv("SLM_SMART_MODEL", "smart")
SLM_TIMEOUT = int(os.getenv("SLM_TIMEOUT_SECONDS", "60"))
MAX_CORRECTIONS = 2  # Nombre maximum de boucles de correction

# Répertoire de sortie pour les tests générés
OUTPUT_DIR = Path("tests/ai_generated")

# ---------------------------------------------------------------------------
# Prompt de génération de tests unitaires
# ---------------------------------------------------------------------------

SYSTEME_PROMPT = """Tu es un expert en tests unitaires Python avec pytest.
Tu écris des tests clairs, maintenables et bien nommés.
Tu utilises unittest.mock pour isoler les dépendances.
Tu replies UNIQUEMENT avec du code Python valide, sans explications ni markdown."""


def _construire_prompt_generation(source_code: str, fichier_nom: str) -> str:
    return f"""Génère des tests unitaires pytest complets pour le fichier suivant.

FICHIER : {fichier_nom}

CODE SOURCE :
```python
{source_code}
```

RÈGLES :
1. Utilise pytest et unittest.mock
2. Nomme chaque test test_<ce_qui_est_testé>_<condition>
3. Teste les cas nominaux ET les cas d'erreur
4. Mocke toutes les dépendances externes (BDD, HTTP, LLM)
5. Chaque test doit être indépendant (pas d'état partagé)
6. Ajoute un commentaire au-dessus de chaque test expliquant ce qu'il vérifie
7. NE fais PAS d'imports inutiles

Réponds UNIQUEMENT avec le code Python des tests, sans markdown."""


def _construire_prompt_correction(
    source_code: str,
    tests_code: str,
    sortie_pytest: str,
) -> str:
    return f"""Les tests suivants ont des erreurs. Corrige-les.

CODE SOURCE TESTÉ :
```python
{source_code}
```

TESTS ACTUELS (avec erreurs) :
```python
{tests_code}
```

ERREURS PYTEST :
{sortie_pytest}

Retourne le code Python corrigé complet (tous les tests, pas seulement ceux corrigés).
Réponds UNIQUEMENT avec le code Python, sans markdown."""


# ---------------------------------------------------------------------------
# Appel LLM
# ---------------------------------------------------------------------------

def _appeler_llm(prompt: str, system: str = SYSTEME_PROMPT) -> str:
    """
    Appelle le LLM via l'endpoint OpenAI-compatible.
    Gestion : timeout, quota (429), clé absente.
    """
    if not SLM_SMART_URL:
        print("❌ Erreur : SLM_SMART_URL n'est pas défini dans .env")
        sys.exit(1)

    url = f"{SLM_SMART_URL.rstrip('/')}/v1/chat/completions"
    payload = {
        "model": SLM_SMART_MODEL,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.2,
        "max_tokens": 4000,
    }

    debut = time.perf_counter()
    try:
        response = httpx.post(
            url,
            json=payload,
            headers={"Authorization": f"Bearer {SLM_API_KEY}"},
            timeout=SLM_TIMEOUT,
        )

        if response.status_code == 429:
            print("⚠️  Quota LLM atteint. Réessayez dans quelques secondes.")
            sys.exit(1)

        response.raise_for_status()
        data = response.json()
        contenu = data["choices"][0]["message"]["content"]
        latence = int((time.perf_counter() - debut) * 1000)
        usage = data.get("usage", {})
        print(f"  → LLM répondu en {latence}ms | tokens: {usage.get('total_tokens', '?')}")
        return contenu, latence, usage.get("total_tokens", 0)

    except httpx.TimeoutException:
        print(f"❌ Timeout LLM après {SLM_TIMEOUT}s")
        sys.exit(1)
    except httpx.HTTPError as exc:
        print(f"❌ Erreur HTTP LLM : {exc}")
        sys.exit(1)


def _nettoyer_code(code: str) -> str:
    """Retire les balises markdown si le LLM les a ajoutées malgré la consigne."""
    code = code.strip()
    if code.startswith("```python"):
        code = code[9:]
    elif code.startswith("```"):
        code = code[3:]
    if code.endswith("```"):
        code = code[:-3]
    return code.strip()


# ---------------------------------------------------------------------------
# Exécution des tests et mesure de couverture
# ---------------------------------------------------------------------------

def _executer_tests(fichier_tests: Path, fichier_source: Path) -> tuple[bool, str, float]:
    """
    Lance pytest sur le fichier de tests généré.
    Retourne (succès, sortie, couverture_pct).
    """
    module_source = str(fichier_source).replace("\\", "/").replace(".py", "").replace("/", ".")

    commande = [
        sys.executable, "-m", "pytest",
        str(fichier_tests),
        "--tb=short",
        "-q",
        f"--cov={fichier_source.parent}",
        "--cov-report=term-missing",
        "--no-header",
    ]

    try:
        resultat = subprocess.run(
            commande,
            capture_output=True,
            text=True,
            timeout=120,
        )
        sortie = resultat.stdout + resultat.stderr
        succes = resultat.returncode == 0

        # Extraire le pourcentage de couverture depuis la sortie pytest-cov
        couverture = 0.0
        for ligne in sortie.split("\n"):
            if "TOTAL" in ligne and "%" in ligne:
                parties = ligne.split()
                for partie in parties:
                    if partie.endswith("%"):
                        try:
                            couverture = float(partie.replace("%", ""))
                            break
                        except ValueError:
                            pass

        return succes, sortie, couverture
    except subprocess.TimeoutExpired:
        return False, "Tests trop longs (timeout 120s)", 0.0


# ---------------------------------------------------------------------------
# Comptage des tests
# ---------------------------------------------------------------------------

def _compter_tests(code_tests: str) -> int:
    """Compte le nombre de fonctions de test dans le code généré."""
    return sum(1 for ligne in code_tests.split("\n") if ligne.strip().startswith("def test_"))


# ---------------------------------------------------------------------------
# Rapport de métriques
# ---------------------------------------------------------------------------

def _sauvegarder_rapport(rapport: dict, fichier_source: Path) -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    nom_rapport = OUTPUT_DIR / f"{fichier_source.stem}_rapport.json"
    with open(nom_rapport, "w", encoding="utf-8") as f:
        json.dump(rapport, f, ensure_ascii=False, indent=2)
    print(f"\n📊 Rapport sauvegardé : {nom_rapport}")


# ---------------------------------------------------------------------------
# Point d'entrée principal
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Génère des tests unitaires par IA pour un fichier Python."
    )
    parser.add_argument(
        "--file", required=True,
        help="Chemin vers le fichier Python à tester (ex: app/coreAgents/Agents/tech_assessment/generator.py)"
    )
    parser.add_argument(
        "--prompt-version", default="v2", choices=["v1", "v2", "v3"],
        help="Version du prompt (v1=basique, v2=structuré, v3=CoT)"
    )
    args = parser.parse_args()

    fichier_source = Path(args.file)
    if not fichier_source.exists():
        print(f"❌ Fichier introuvable : {fichier_source}")
        sys.exit(1)

    print(f"\n🤖 Génération de tests pour : {fichier_source}")
    print(f"   Version prompt : {args.prompt_version}")
    print("-" * 60)

    # Lire le code source
    source_code = fichier_source.read_text(encoding="utf-8")

    # Limiter la taille pour éviter les débordements de contexte
    if len(source_code) > 8000:
        print(f"⚠️  Fichier tronqué à 8000 caractères (taille originale : {len(source_code)})")
        source_code = source_code[:8000] + "\n# ... (tronqué)"

    # Étape 1 : Générer les tests
    print("\n📝 Étape 1 : Génération des tests...")
    prompt = _construire_prompt_generation(source_code, fichier_source.name)
    code_tests, latence_generation, tokens_generation = _appeler_llm(prompt)
    code_tests = _nettoyer_code(code_tests)

    nb_tests_generes = _compter_tests(code_tests)
    print(f"   → {nb_tests_generes} tests générés")

    # Sauvegarder les tests générés
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    fichier_tests = OUTPUT_DIR / f"test_{fichier_source.stem}_ai.py"
    fichier_tests.write_text(code_tests, encoding="utf-8")
    print(f"   → Tests sauvegardés : {fichier_tests}")

    # Étape 2 : Exécuter les tests
    print("\n🧪 Étape 2 : Exécution des tests...")
    succes, sortie_pytest, couverture = _executer_tests(fichier_tests, fichier_source)

    latence_totale = latence_generation
    tokens_totaux = tokens_generation
    nb_corrections = 0
    tests_passes_premier_coup = nb_tests_generes if succes else 0

    if succes:
        print(f"   ✅ Tous les tests passent ! Couverture : {couverture:.1f}%")
    else:
        print(f"   ⚠️  Des tests échouent. Lancement de la boucle de correction...")

        # Étape 3 : Boucle de correction (max MAX_CORRECTIONS fois)
        for iteration in range(1, MAX_CORRECTIONS + 1):
            print(f"\n🔧 Correction {iteration}/{MAX_CORRECTIONS}...")
            prompt_correction = _construire_prompt_correction(source_code, code_tests, sortie_pytest)
            code_tests_corrige, latence_corr, tokens_corr = _appeler_llm(prompt_correction)
            code_tests_corrige = _nettoyer_code(code_tests_corrige)

            latence_totale += latence_corr
            tokens_totaux += tokens_corr
            nb_corrections += 1

            # Sauvegarder et retester
            fichier_tests.write_text(code_tests_corrige, encoding="utf-8")
            succes, sortie_pytest, couverture = _executer_tests(fichier_tests, fichier_source)
            code_tests = code_tests_corrige

            if succes:
                print(f"   ✅ Tests corrigés ! Couverture : {couverture:.1f}%")
                break
            else:
                print(f"   ❌ Encore des échecs après la correction {iteration}")

    if not succes:
        print("\n⚠️  Des tests échouent encore après les corrections automatiques.")
        print("   → Ces tests nécessitent une relecture manuelle (tests_corriges_manuellement).")

    # Rapport de métriques
    rapport = {
        "fichier_source": str(fichier_source),
        "prompt_version": args.prompt_version,
        "tests_generes": nb_tests_generes,
        "tests_passes_premier_coup": tests_passes_premier_coup,
        "tests_apres_correction_ia": _compter_tests(code_tests) if succes else 0,
        "tests_corriges_manuellement": 0,  # À remplir manuellement après relecture
        "nb_boucles_correction_ia": nb_corrections,
        "succes_final": succes,
        "couverture_pct": couverture,
        "latence_totale_ms": latence_totale,
        "tokens_totaux_estimes": tokens_totaux,
        "temps_gagne_estime_min": max(0, nb_tests_generes * 3 - latence_totale / 60000),
        "fichier_tests_genere": str(fichier_tests),
    }

    print("\n📊 Métriques :")
    print(f"   Tests générés        : {rapport['tests_generes']}")
    print(f"   Passent 1er coup     : {rapport['tests_passes_premier_coup']}")
    print(f"   Corrections IA       : {rapport['nb_boucles_correction_ia']}")
    print(f"   Couverture           : {rapport['couverture_pct']:.1f}%")
    print(f"   Latence totale LLM   : {rapport['latence_totale_ms']}ms")
    print(f"   Tokens estimés       : {rapport['tokens_totaux_estimes']}")

    _sauvegarder_rapport(rapport, fichier_source)

    print("\n⚠️  IMPORTANT : Relisez les tests générés avant de les commiter !")
    print("   Ajustez 'tests_corriges_manuellement' dans le rapport.")
    print(f"\n   Fichier de tests : {fichier_tests}")

    sys.exit(0 if succes else 1)


if __name__ == "__main__":
    main()
