"""Limites du pipeline d'analyse de CV (Module 1)."""

# Même limite que Spring (app.cv.max-size-bytes) : FastAPI revérifie, il ne fait pas confiance à l'appelant.
MAX_PDF_BYTES = 5 * 1024 * 1024

# Un CV dépasse rarement 4 pages ; au-delà, on refuse plutôt que d'envoyer un document entier au LLM.
MAX_PAGES = 10

# Texte transmis aux étapes suivantes (LLM) : tronqué au-delà, pour maîtriser le coût et la latence.
MAX_TEXT_CHARS = 20_000

# En dessous, le PDF est probablement scanné (image sans texte) : pas d'OCR dans ce module.
MIN_TEXT_CHARS = 50

SPACY_MODEL = "fr_core_news_md"

# Termes techniques que le NER de spaCy confond parfois avec des noms de personnes ou de lieux.
# Ils ne doivent jamais être masqués : ce sont justement les compétences à extraire.
TECH_TERMS = frozenset({
    "angular", "ansible", "api", "aws", "azure", "bootstrap", "c", "c#", "c++", "css", "django",
    "docker", "excel", "express", "fastapi", "figma", "firebase", "flask", "flutter", "gcp", "git",
    "github", "gitlab", "go", "graphql", "hibernate", "html", "java", "javascript", "jenkins",
    "jira", "jquery", "kafka", "keras", "kotlin", "kubernetes", "laravel", "linux", "mariadb",
    "matlab", "maven", "mongodb", "mysql", "nestjs", "next.js", "nginx", "node", "node.js",
    "nodejs", "numpy", "oracle", "pandas", "php", "postgresql", "postman", "power bi", "python",
    "pytorch", "rabbitmq", "react", "react native", "reactjs", "redis", "rest", "ruby", "rust",
    "sass", "scala", "scrum", "selenium", "spring", "spring boot", "springboot", "sql",
    "sql server", "swift", "symfony", "tailwind", "tensorflow", "terraform", "typescript",
    "unity", "vue", "vue.js", "windows", "wordpress",
})

# Titres de sections de CV : avec leur majuscule, le NER les prend parfois pour des lieux
# (« Compétences » -> LOC). Le LLM en a besoin pour comprendre la structure du CV.
CV_SECTION_TERMS = frozenset({
    "à propos", "activités", "centres d'intérêt", "certifications", "compétences",
    "compétences techniques", "contact", "coordonnées", "diplômes", "éducation", "expérience",
    "expériences", "expérience professionnelle", "expériences professionnelles", "formation",
    "formations", "intérêts", "langues", "loisirs", "outils", "parcours", "profil", "projets",
    "qualités", "références", "résumé", "soft skills", "stages", "technologies",
})
