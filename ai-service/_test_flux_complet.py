"""
Test complet du flux Module 4 :
  1. Generer un test (POST /api/v1/tests/generate)
  2. Soumettre des reponses (POST /api/v1/tests/{id}/submit)
  3. Afficher le score et le feedback
"""
import httpx, json

BASE = "http://127.0.0.1:8080"

# ── ETAPE 1 : Generer un test ──────────────────────────────────────────────
print("=" * 60)
print("ETAPE 1 : Generation du test par le LLM...")
print("=" * 60)

body_generate = {
    "job_title": "Developpeur Backend Python",
    "job_description": "Nous recherchons un developpeur Python junior pour notre equipe backend.",
    "competences_manquantes": [
        {"skill": "Docker", "level": "d\u00e9butant"},
        {"skill": "FastAPI", "level": "interm\u00e9diaire"}
    ],
    "candidate_id": "cand-anonyme-001"
}

r1 = httpx.post(f"{BASE}/api/v1/tests/generate", json=body_generate, timeout=90)
print(f"Status : {r1.status_code}")

if r1.status_code != 201:
    print("ERREUR generation :", r1.json())
    exit(1)

test = r1.json()
test_id = test["test_id"]
print(f"test_id : {test_id}")
print(f"Titre   : {test['titre']}")
print(f"Questions : {len(test['questions'])}")
print()

# ── ETAPE 2 : Soumettre des reponses ──────────────────────────────────────
print("=" * 60)
print("ETAPE 2 : Soumission des reponses du candidat...")
print("=" * 60)

body_submit = {
    "candidate_id": "cand-anonyme-001",
    "reponses_questions": [
        {"numero": 1, "reponse": "A"},
        {"numero": 2, "reponse": "A"},
        {"numero": 3, "reponse": "A"},
        {"numero": 4, "reponse": "Pour creer un conteneur Docker : docker pull pour recuperer l image, docker run pour lancer le conteneur. On utilise docker ps pour voir les conteneurs actifs et docker stop pour les arreter."},
        {"numero": 5, "reponse": "Dans FastAPI, on cree une classe heritant de BaseModel de Pydantic avec des champs types. FastAPI utilise automatiquement ce modele pour valider les donnees de la requete et renvoie une erreur 422 si les donnees sont invalides."}
    ],
    "reponse_exercice": "from fastapi import FastAPI, HTTPException\nfrom pydantic import BaseModel\n\napp = FastAPI()\n\nclass Item(BaseModel):\n    id: int\n    name: str\n    price: float\n\n@app.get('/items/{item_id}')\ndef get_item(item_id: int):\n    if item_id <= 0:\n        raise HTTPException(status_code=400, detail='item_id doit etre positif')\n    return Item(id=item_id, name='exemple', price=9.99)"
}

r2 = httpx.post(f"{BASE}/api/v1/tests/{test_id}/submit", json=body_submit, timeout=90)
print(f"Status : {r2.status_code}")

if r2.status_code != 200:
    print("ERREUR soumission :", r2.json())
    exit(1)

result = r2.json()
eval_data = result.get("evaluation", {})

print()
print("=" * 60)
print("RESULTAT DE LA CORRECTION PAR L IA")
print("=" * 60)
print(f"Score          : {eval_data.get('score')}/100")
print(f"Recommandation : {eval_data.get('recommandation')}")
print()
print("Points forts :")
for p in eval_data.get("points_forts", []):
    print(f"  + {p}")
print()
print("Points faibles :")
for p in eval_data.get("points_faibles", []):
    print(f"  - {p}")
print()
print("Feedback :")
print(f"  {eval_data.get('feedback', '')[:300]}")
print()
print("SUCCES - Flux complet fonctionne !")
