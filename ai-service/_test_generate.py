import httpx, json

body = {
    "job_title": "Developpeur Backend Python",
    "job_description": "Nous recherchons un developpeur Python junior pour notre equipe backend.",
    "competences_manquantes": [
        {"skill": "Docker", "level": "d\u00e9butant"},
        {"skill": "FastAPI", "level": "interm\u00e9diaire"}
    ],
    "candidate_id": "cand-anonyme-001"
}

print("Appel LLM en cours...")
r = httpx.post("http://127.0.0.1:8080/api/v1/tests/generate", json=body, timeout=90)
print("Status:", r.status_code)

if r.status_code == 201:
    data = r.json()
    print()
    print("test_id :", data.get("test_id"))
    print("Titre   :", data.get("titre"))
    print()
    for i, q in enumerate(data.get("questions", []), 1):
        qtype = q.get("type", "?")
        enonce = q.get("enonce", "")[:60]
        print(f"  Q{i} [{qtype}] {enonce}...")
    print()
    ex = data.get("exercice", {})
    print("Exercice:", ex.get("enonce", "")[:70])
    print()
    print("SUCCES - Le LLM a genere le test !")
    # Sauvegarder le test_id pour le test suivant
    with open("_last_test_id.txt", "w") as f:
        f.write(data.get("test_id", ""))
    print("test_id sauvegarde dans _last_test_id.txt")
else:
    print("Erreur:", json.dumps(r.json(), ensure_ascii=False, indent=2)[:500])
