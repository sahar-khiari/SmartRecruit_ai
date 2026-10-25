"""Référentiel de compétences IT utilisé pour la suggestion par similarité (embeddings).

Chaque entrée : (nom normalisé, catégorie).
"""

COMPETENCES: list[tuple[str, str]] = [
    # Langages
    ("Java", "Langage"), ("Python", "Langage"), ("JavaScript", "Langage"), ("TypeScript", "Langage"),
    ("C", "Langage"), ("C++", "Langage"), ("C#", "Langage"), ("PHP", "Langage"), ("Go", "Langage"),
    ("Kotlin", "Langage"), ("Swift", "Langage"), ("Dart", "Langage"), ("Rust", "Langage"),
    ("Scala", "Langage"), ("R", "Langage"), ("Ruby", "Langage"), ("SQL", "Langage"), ("Bash", "Langage"),
    # Back-end
    ("Spring Boot", "Back-end"), ("Spring Security", "Back-end"), ("Hibernate / JPA", "Back-end"),
    ("Node.js", "Back-end"), ("Express.js", "Back-end"), ("NestJS", "Back-end"), ("Django", "Back-end"),
    ("Flask", "Back-end"), ("FastAPI", "Back-end"), ("Laravel", "Back-end"), ("Symfony", "Back-end"),
    (".NET / ASP.NET Core", "Back-end"), ("API REST", "Back-end"), ("GraphQL", "Back-end"),
    ("Microservices", "Back-end"), ("gRPC", "Back-end"), ("JWT / OAuth2", "Back-end"),
    # Front-end
    ("Angular", "Front-end"), ("React", "Front-end"), ("Vue.js", "Front-end"), ("Next.js", "Front-end"),
    ("HTML5", "Front-end"), ("CSS3", "Front-end"), ("Sass", "Front-end"), ("Tailwind CSS", "Front-end"),
    ("Bootstrap", "Front-end"), ("RxJS", "Front-end"), ("Redux", "Front-end"),
    # Mobile
    ("Flutter", "Mobile"), ("React Native", "Mobile"), ("Android", "Mobile"), ("iOS", "Mobile"),
    # Bases de données
    ("MySQL", "Base de données"), ("PostgreSQL", "Base de données"), ("MariaDB", "Base de données"),
    ("Oracle", "Base de données"), ("SQL Server", "Base de données"), ("MongoDB", "Base de données"),
    ("Redis", "Base de données"), ("Elasticsearch", "Base de données"), ("Cassandra", "Base de données"),
    # DevOps / Cloud
    ("Docker", "DevOps"), ("Kubernetes", "DevOps"), ("CI/CD", "DevOps"), ("GitHub Actions", "DevOps"),
    ("GitLab CI", "DevOps"), ("Jenkins", "DevOps"), ("Terraform", "DevOps"), ("Ansible", "DevOps"),
    ("Linux", "DevOps"), ("Nginx", "DevOps"), ("AWS", "Cloud"), ("Microsoft Azure", "Cloud"),
    ("Google Cloud", "Cloud"), ("Prometheus / Grafana", "DevOps"),
    # Data / IA
    ("Machine Learning", "Data / IA"), ("Deep Learning", "Data / IA"), ("TensorFlow", "Data / IA"),
    ("PyTorch", "Data / IA"), ("scikit-learn", "Data / IA"), ("Pandas", "Data / IA"),
    ("NumPy", "Data / IA"), ("NLP", "Data / IA"), ("LLM / IA générative", "Data / IA"),
    ("Power BI", "Data / IA"), ("Spark", "Data / IA"), ("ETL", "Data / IA"), ("Data Warehouse", "Data / IA"),
    # Qualité / tests
    ("JUnit", "Tests"), ("Mockito", "Tests"), ("Selenium", "Tests"), ("Cypress", "Tests"),
    ("Jest", "Tests"), ("pytest", "Tests"), ("Tests unitaires", "Tests"), ("SonarQube", "Tests"),
    # Sécurité / réseau
    ("Cybersécurité", "Sécurité"), ("OWASP", "Sécurité"), ("Pentest", "Sécurité"),
    ("Réseaux TCP/IP", "Réseau"), ("Cisco", "Réseau"),
    # Méthodes et outils
    ("Git", "Outils"), ("Jira", "Outils"), ("Maven", "Outils"), ("Gradle", "Outils"),
    ("Scrum", "Méthodes"), ("Agile", "Méthodes"), ("UML", "Méthodes"), ("Design patterns", "Méthodes"),
    ("Clean Code", "Méthodes"), ("Architecture logicielle", "Méthodes"), ("DevSecOps", "Méthodes"),
    # Compétences transverses
    ("Travail en équipe", "Soft skill"), ("Communication", "Soft skill"), ("Autonomie", "Soft skill"),
    ("Résolution de problèmes", "Soft skill"), ("Anglais technique", "Soft skill"),
    ("Gestion de projet", "Soft skill"), ("Leadership", "Soft skill"),
]
