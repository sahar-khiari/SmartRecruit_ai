package com.smartrecruit.matching.engine;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Dictionnaire de compétences techniques avec alias et normalisation.
 *
 * Permet de réconcilier des variantes textuelles :
 *   "spring boot" ↔ "spring", "springboot"
 *   "js" ↔ "javascript"
 *   "postgres" ↔ "postgresql"
 *
 * Chaque compétence canonique est associée à une liste d'alias (en FR et EN).
 * La méthode extractSkills renvoie la liste de formes canoniques trouvées
 * dans un texte.
 */
@Component
public class SkillDictionary {

    /**
     * Map : alias en minuscules → forme canonique.
     * Remplie à la construction à partir de CANONICAL_SKILLS.
     */
    private final Map<String, String> aliasToCanonical = new HashMap<>();

    /**
     * Compétences canoniques → liste d'alias (incluant la forme canonique elle-même).
     * Couvre FR et EN, formats courts et longs.
     */
    private static final List<String[]> CANONICAL_SKILLS = List.of(
            // Langages
            new String[]{"java",          "java", "jdk"},
            new String[]{"python",        "python", "py"},
            new String[]{"javascript",    "javascript", "js", "ecmascript"},
            new String[]{"typescript",    "typescript", "ts"},
            new String[]{"csharp",        "c#", "csharp", "c sharp", "dotnet csharp"},
            new String[]{"cpp",           "c++", "cpp", "cplusplus"},
            new String[]{"go",            "golang", "go"},
            new String[]{"kotlin",        "kotlin"},
            new String[]{"swift",         "swift"},
            new String[]{"rust",          "rust"},
            new String[]{"scala",         "scala"},
            new String[]{"r",             " r ", "langage r", "r language"},
            new String[]{"php",           "php"},
            new String[]{"ruby",          "ruby", "ruby on rails", "rails"},
            // Frameworks Java
            new String[]{"spring",        "spring", "spring framework"},
            new String[]{"spring boot",   "spring boot", "springboot", "spring-boot"},
            new String[]{"spring security","spring security"},
            new String[]{"hibernate",     "hibernate", "jpa", "jakarta persistence"},
            new String[]{"quarkus",       "quarkus"},
            new String[]{"micronaut",     "micronaut"},
            // Frameworks Python
            new String[]{"django",        "django"},
            new String[]{"fastapi",       "fastapi", "fast api"},
            new String[]{"flask",         "flask"},
            new String[]{"pytorch",       "pytorch", "torch"},
            new String[]{"tensorflow",    "tensorflow", "tf"},
            new String[]{"scikit-learn",  "scikit-learn", "sklearn", "scikit learn"},
            new String[]{"pandas",        "pandas"},
            new String[]{"numpy",         "numpy"},
            // Web / Frontend
            new String[]{"angular",       "angular", "angularjs"},
            new String[]{"react",         "react", "reactjs", "react.js"},
            new String[]{"vue",           "vue", "vuejs", "vue.js"},
            new String[]{"html",          "html", "html5"},
            new String[]{"css",           "css", "css3", "scss", "sass"},
            new String[]{"nodejs",        "node.js", "nodejs", "node js"},
            // Bases de données
            new String[]{"sql",           "sql"},
            new String[]{"mysql",         "mysql"},
            new String[]{"postgresql",    "postgresql", "postgres", "psql"},
            new String[]{"mariadb",       "mariadb"},
            new String[]{"mongodb",       "mongodb", "mongo"},
            new String[]{"redis",         "redis"},
            new String[]{"elasticsearch", "elasticsearch", "elastic", "es"},
            new String[]{"oracle",        "oracle", "oracle db"},
            new String[]{"sqlite",        "sqlite"},
            // DevOps / Cloud
            new String[]{"docker",        "docker", "conteneur", "container"},
            new String[]{"kubernetes",    "kubernetes", "k8s"},
            new String[]{"aws",           "aws", "amazon web services"},
            new String[]{"azure",         "azure", "microsoft azure"},
            new String[]{"gcp",           "gcp", "google cloud", "google cloud platform"},
            new String[]{"ci/cd",         "ci/cd", "cicd", "intégration continue", "continuous integration"},
            new String[]{"jenkins",       "jenkins"},
            new String[]{"gitlab ci",     "gitlab ci", "gitlab-ci"},
            new String[]{"github actions","github actions"},
            new String[]{"terraform",     "terraform"},
            new String[]{"ansible",       "ansible"},
            // Pratiques
            new String[]{"git",           "git"},
            new String[]{"agile",         "agile", "méthode agile", "scrum", "kanban"},
            new String[]{"tdd",           "tdd", "test driven", "test-driven"},
            new String[]{"rest",          "rest", "restful", "api rest", "rest api"},
            new String[]{"graphql",       "graphql"},
            new String[]{"microservices", "microservices", "micro-services"},
            new String[]{"architecture hexagonale", "architecture hexagonale", "hexagonal"},
            new String[]{"design patterns","design patterns", "patron de conception"},
            // Outils
            new String[]{"maven",         "maven"},
            new String[]{"gradle",        "gradle"},
            new String[]{"jira",          "jira"},
            new String[]{"linux",         "linux", "unix"},
            new String[]{"kafka",         "kafka", "apache kafka"},
            new String[]{"rabbitmq",      "rabbitmq"}
    );

    public SkillDictionary() {
        // Remplissage de la map alias → canonique
        for (String[] entry : CANONICAL_SKILLS) {
            String canonical = entry[0];
            for (int i = 1; i < entry.length; i++) {
                aliasToCanonical.put(normalize(entry[i]), canonical);
            }
        }
    }

    /**
     * Extrait la liste de compétences canoniques trouvées dans un texte.
     * La recherche est insensible à la casse et cherche les alias comme
     * sous-chaînes délimitées par des caractères non alphanumériques.
     *
     * @param text texte brut (CV ou description de poste)
     * @return ensemble ordonné de compétences canoniques détectées
     */
    public Set<String> extractSkills(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();

        String normalizedText = " " + normalize(text) + " ";
        Set<String> found = new LinkedHashSet<>();

        // On teste chaque alias connu (du plus long au plus court pour éviter
        // qu'un alias court masque un alias long, ex. "r" avant "ruby").
        List<Map.Entry<String, String>> entries = new ArrayList<>(aliasToCanonical.entrySet());
        entries.sort((a, b) -> b.getKey().length() - a.getKey().length());

        for (Map.Entry<String, String> e : entries) {
            String alias = e.getKey();
            // Délimiteur : espace ou ponctuation autour de l'alias
            String pattern = "[^a-z0-9]" + java.util.regex.Pattern.quote(alias) + "[^a-z0-9]";
            if (java.util.regex.Pattern.compile(pattern).matcher(normalizedText).find()) {
                found.add(e.getValue());
            }
        }
        return found;
    }

    /**
     * Retourne la forme canonique d'un alias, ou null si inconnu.
     */
    public String canonical(String alias) {
        return aliasToCanonical.get(normalize(alias));
    }

    /** Retourne toutes les compétences canoniques connues. */
    public Set<String> allCanonical() {
        return new LinkedHashSet<>(aliasToCanonical.values());
    }

    private String normalize(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).trim();
    }
}
