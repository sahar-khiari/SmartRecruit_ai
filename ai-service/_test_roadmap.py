"""Test de la route POST /api/v1/roadmap"""
import httpx, json

BASE = "http://127.0.0.1:8080"

body = {
    "candidate_id": "cand-anonyme-001",
    "competences_manquantes": [
        {"skill": "Docker", "level": "d\u00e9butant"},
        {"skill": "FastAPI", "level": "interm\u00e9diaire"}
    ],
    "duree_plan_semaines": 8,
    "evaluation": {
        "score": 46,
        "score_questions": 36,
        "score_exercice": 10,
        "recommandation": "APPROFONDIR",
        "points_forts": ["Bases Docker", "Validation Pydantic"],
        "points_faibles": ["docker-compose", "creation image Docker", "exercice incomplet"],
        "feedback": "Bonnes bases mais besoin d approfondissement."
    }
}

print("Generation de la feuille de route...")
r = httpx.post(f"{BASE}/api/v1/roadmap", json=body, timeout=90)
print(f"Status : {r.status_code}")

if r.status_code == 201:
    data = r.json()
    roadmap = data.get("roadmap", {})
    print()
    print("=" * 60)
    print("FEUILLE DE ROUTE PERSONNALISEE (8 semaines)")
    print("=" * 60)
    semaines = roadmap.get("semaines", [])
    for s in semaines[:4]:  # Afficher les 4 premieres semaines
        print(f"\nSemaine {s.get('numero')} : {s.get('objectif')}")
        for comp in s.get("competences", []):
            print(f"  Competence : {comp}")
        for res in s.get("ressources", [])[:2]:
            print(f"  Ressource  : {res}")
        if s.get("mini_projet"):
            print(f"  Mini-projet: {s['mini_projet']}")
    if len(semaines) > 4:
        print(f"\n... + {len(semaines)-4} autres semaines")
    print()
    print("SUCCES - Roadmap generee !")
else:
    print("Erreur:", json.dumps(r.json(), ensure_ascii=False, indent=2)[:400])
