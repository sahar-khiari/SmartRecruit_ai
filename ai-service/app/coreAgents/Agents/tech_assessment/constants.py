# Constantes partagées par tous les sous-modules du module 4
# Modifiables sans toucher à la logique métier.

# Nombre de questions QCM/ouvertes dans un test généré
NB_QUESTIONS = 5

# Nombre d'exercices pratiques dans un test
NB_EXERCICES = 1

# Durée maximale recommandée d'un test (en minutes)
DUREE_TEST_MINUTES = 45

# Caller passé au SLMClient pour identifier ce module dans les logs model_calls
CALLER_GENERATE = "tech_assessment.generator"
CALLER_EVALUATE = "tech_assessment.evaluator"
CALLER_ROADMAP = "tech_assessment.roadmap"

# Profil LLM par défaut (nano = rapide/économique, smart = meilleur pour générer des tests)
PROFILE_GENERATE = "smart"
PROFILE_EVALUATE = "smart"
PROFILE_ROADMAP = "fast"
