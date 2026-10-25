package com.smartrecruit.matching.engine;

import com.smartrecruit.matching.entity.MatchingCritere;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Moteur de matching Layer 0 : 100 % Java, sans dépendance externe.
 *
 * Algorithme :
 *  1. Filtre dur sur les critères obligatoires → score = 0 si non satisfait.
 *  2. Extraction de compétences dans le CV et la description de poste.
 *  3. Calcul TF-IDF cosinus entre CV et texte de l'offre.
 *  4. Score de couverture des compétences : |cap(CV,offre)| / |offre|
 *  5. Score d'adéquation du niveau : comparaison JUNIOR < INTERMEDIAIRE < SENIOR.
 *  6. Score du test technique (Candidature.scoreIa) normalisé sur 100.
 *  7. Fusion pondérée :
 *       final = w1*tfidf + w2*skills + w3*niveau + w4*test
 *     Si le score test est absent, les poids sont renormalisés (w4=0).
 *  8. Génération d'un verdict et d'une explication textuelle en français.
 *
 * Anonymisation du CV AVANT tout envoi hors Spring (pour les layers 1–2).
 * Ce moteur ne quitte jamais Spring et ne stocke aucune donnée personnelle.
 */
@Component
public class JavaMatchingEngine {

    // --- Poids par défaut de la fusion (renormalisés si test absent) ---
    private static final double W_TFIDF    = 0.35;
    private static final double W_SKILLS   = 0.35;
    private static final double W_NIVEAU   = 0.15;
    private static final double W_TEST     = 0.15;

    // Ordre des niveaux
    private static final List<String> NIVEAU_ORDER =
            List.of("JUNIOR", "INTERMEDIAIRE", "INTERMEDIAIRE+", "SENIOR", "EXPERT");

    // Regex d'anonymisation (emails, tél. FR/international, URLs)
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?:\\+?33|0)[\\s.\\-]?[1-9](?:[\\s.\\-]?\\d{2}){4}");
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s]+");

    private final TfIdfCalculator tfidf;
    private final SkillDictionary skillDict;

    public JavaMatchingEngine(TfIdfCalculator tfidf, SkillDictionary skillDict) {
        this.tfidf     = tfidf;
        this.skillDict = skillDict;
    }

    /**
     * Calcule le matching entre un profil candidat et une offre d'emploi.
     *
     * @param cvText          texte brut du CV (NE PAS anonymiser avant : on le fait ici)
     * @param offreTexte      texte complet de l'offre (titre + description + compétences)
     * @param niveauCandidat  niveau du candidat (JUNIOR/INTERMEDIAIRE/SENIOR)
     * @param niveauOffre     niveau requis par l'offre
     * @param scoreTestIa     score du test technique (0–100) ou null si absent
     * @param criteres        critères de matching définis par le recruteur
     * @return résultat complet avec scores, verdict et explication
     */
    public MatchingResultLayer0 compute(
            String cvText,
            String offreTexte,
            String niveauCandidat,
            String niveauOffre,
            Integer scoreTestIa,
            List<MatchingCritere> criteres) {

        // 1. Anonymisation du CV avant traitement
        String cvAnonymise = anonymize(cvText == null ? "" : cvText);

        // 2. Extraction des compétences
        Set<String> skillsCV    = skillDict.extractSkills(cvAnonymise);
        Set<String> skillsOffre = skillDict.extractSkills(offreTexte == null ? "" : offreTexte);

        // 3. Filtre dur sur les critères obligatoires
        for (MatchingCritere c : criteres) {
            if (c.isObligatoire()) {
                String canon = skillDict.canonical(c.getNom());
                boolean present = canon != null
                        ? skillsCV.contains(canon)
                        : cvAnonymise.toLowerCase(Locale.ROOT).contains(c.getNom().toLowerCase(Locale.ROOT));
                if (!present) {
                    // Filtre dur non satisfait → score = 0
                    String explication = "❌ Critère obligatoire non satisfait : « " + c.getNom()
                            + " » est requis mais absent du profil.";
                    return new MatchingResultLayer0(0.0, 0.0, 0.0, 0.0, -1.0,
                            "WEAK", Collections.emptySet(), Set.of(c.getNom()),
                            explication, buildBreakdown(0,0,0,-1));
                }
            }
        }

        // 4. Score TF-IDF
        double tfidfScore = tfidf.similarity(cvAnonymise, offreTexte == null ? "" : offreTexte);

        // 5. Score de couverture des compétences
        double skillsScore = computeSkillsCoverage(skillsCV, skillsOffre, criteres);

        // 6. Compétences présentes / manquantes
        Set<String> matched = new LinkedHashSet<>(skillsCV);
        matched.retainAll(skillsOffre);
        Set<String> missing = new LinkedHashSet<>(skillsOffre);
        missing.removeAll(skillsCV);

        // 7. Score de niveau
        double niveauScore = computeNiveauScore(niveauCandidat, niveauOffre);

        // 8. Score test technique (normalisé 0–1)
        double testScoreNorm = scoreTestIa != null ? Math.min(1.0, scoreTestIa / 100.0) : -1.0;

        // 9. Fusion pondérée
        double finalScore = fusionner(tfidfScore, skillsScore, niveauScore, testScoreNorm);

        // 10. Verdict
        String verdict = finalScore >= 0.70 ? "STRONG_MATCH"
                       : finalScore >= 0.40 ? "POSSIBLE"
                       : "WEAK";

        // 11. Explication
        String explication = genererExplication(
                finalScore, tfidfScore, skillsScore, niveauScore, testScoreNorm,
                matched, missing, verdict, niveauCandidat, niveauOffre);

        return new MatchingResultLayer0(
                round(finalScore), round(tfidfScore), round(skillsScore),
                round(niveauScore), testScoreNorm >= 0 ? round(testScoreNorm) : -1.0,
                verdict, matched, missing, explication,
                buildBreakdown(tfidfScore, skillsScore, niveauScore, testScoreNorm));
    }

    // --- Calculs internes ---

    private double computeSkillsCoverage(
            Set<String> skillsCV, Set<String> skillsOffre, List<MatchingCritere> criteres) {

        if (skillsOffre.isEmpty()) return 0.5; // Pas de compétences définies → score neutre

        if (!criteres.isEmpty()) {
            // Utilise les poids des critères si disponibles
            double totalPoids = 0.0;
            double poidsObtenu = 0.0;
            for (MatchingCritere c : criteres) {
                String canon = skillDict.canonical(c.getNom());
                boolean found = canon != null
                        ? skillsCV.contains(canon)
                        : skillsCV.stream().anyMatch(s -> s.equalsIgnoreCase(c.getNom()));
                totalPoids += c.getPoids();
                if (found) poidsObtenu += c.getPoids();
            }
            return totalPoids > 0 ? poidsObtenu / totalPoids : 0.0;
        }

        // Fallback : taux de recouvrement simple
        long matching = skillsCV.stream().filter(skillsOffre::contains).count();
        return (double) matching / skillsOffre.size();
    }

    private double computeNiveauScore(String niveauCandidat, String niveauOffre) {
        if (niveauCandidat == null || niveauOffre == null) return 0.5;

        int indexCandidat = niveauIndex(niveauCandidat);
        int indexOffre    = niveauIndex(niveauOffre);

        if (indexCandidat < 0 || indexOffre < 0) return 0.5; // Inconnu → neutre

        int diff = indexCandidat - indexOffre;
        if (diff == 0)  return 1.0; // Niveau parfait
        if (diff == 1)  return 0.85; // Sur-qualifié d'un cran (acceptable)
        if (diff == -1) return 0.60; // Sous-qualifié d'un cran (possible avec motivation)
        if (diff >= 2)  return 0.90; // Sur-qualifié de plus d'un cran (moins bon fit)
        return 0.20; // Trop sous-qualifié
    }

    private int niveauIndex(String niveau) {
        if (niveau == null) return -1;
        String n = niveau.toUpperCase(Locale.ROOT).trim()
                .replace("É", "E").replace("è", "e");
        // Normalisation des synonymes fréquents
        n = n.replace("INTERMEDIAIRE", "INTERMEDIAIRE")
             .replace("INTERMÉDIAIRE", "INTERMEDIAIRE")
             .replace("INTERMEDIAIRE+", "INTERMEDIAIRE+");
        for (int i = 0; i < NIVEAU_ORDER.size(); i++) {
            if (NIVEAU_ORDER.get(i).equals(n)) return i;
        }
        return -1;
    }

    private double fusionner(double tfidfScore, double skillsScore,
                             double niveauScore, double testScore) {
        boolean hasTest = testScore >= 0;
        double w1 = W_TFIDF, w2 = W_SKILLS, w3 = W_NIVEAU, w4 = hasTest ? W_TEST : 0.0;
        double total = w1 + w2 + w3 + w4;
        return (w1 * tfidfScore + w2 * skillsScore + w3 * niveauScore
                + (hasTest ? w4 * testScore : 0.0)) / total;
    }

    private String genererExplication(
            double finalScore, double tfidfScore, double skillsScore,
            double niveauScore, double testScore,
            Set<String> matched, Set<String> missing,
            String verdict, String niveauCandidat, String niveauOffre) {

        StringBuilder sb = new StringBuilder();

        sb.append("## Résultat du matching\n\n");
        sb.append(String.format("**Score global : %.0f%%**  |  Verdict : **%s**%n%n",
                finalScore * 100, verdictLabel(verdict)));

        sb.append("### Points forts\n");
        if (!matched.isEmpty()) {
            sb.append("✅ Compétences clés présentes : ")
              .append(String.join(", ", matched.stream().limit(8).collect(Collectors.toList())))
              .append("\n");
        }
        if (tfidfScore >= 0.5) {
            sb.append(String.format("✅ Bonne adéquation textuelle avec l'offre (%.0f%%)%n",
                    tfidfScore * 100));
        }
        if (niveauScore >= 0.8) {
            sb.append("✅ Niveau d'expérience adéquat\n");
        }
        if (testScore >= 0.7) {
            sb.append(String.format("✅ Score test technique élevé : %.0f%%%n", testScore * 100));
        }

        sb.append("\n### Points d'amélioration\n");
        if (!missing.isEmpty()) {
            sb.append("⚠️ Compétences requises non détectées : ")
              .append(String.join(", ", missing.stream().limit(6).collect(Collectors.toList())))
              .append("\n");
        }
        if (tfidfScore < 0.3) {
            sb.append("⚠️ Adéquation textuelle faible avec la description de poste\n");
        }
        if (niveauScore < 0.5) {
            sb.append(String.format("⚠️ Écart de niveau : candidat %s / offre %s%n",
                    niveauCandidat, niveauOffre));
        }

        if (!missing.isEmpty()) {
            sb.append("\n### Plan de montée en compétences suggéré\n");
            missing.stream().limit(4).forEach(skill ->
                sb.append("- Renforcer **").append(skill).append("** : ")
                  .append(actionSuggestion(skill)).append("\n"));
        }

        sb.append("\n*Analyse générée automatiquement par le moteur Java (Layer 0).*");
        return sb.toString();
    }

    private String verdictLabel(String verdict) {
        return switch (verdict) {
            case "STRONG_MATCH" -> "🟢 Fort potentiel";
            case "POSSIBLE"     -> "🟡 Profil intéressant";
            default             -> "🔴 Inadéquat";
        };
    }

    private String actionSuggestion(String skill) {
        // Suggestions génériques par catégorie
        if (List.of("java","spring","spring boot","hibernate").contains(skill))
            return "formation Java/Spring (Udemy, Baeldung), projets pratiques";
        if (List.of("python","django","fastapi","flask").contains(skill))
            return "cours Python officiel, projet Django ou FastAPI";
        if (List.of("docker","kubernetes","ci/cd").contains(skill))
            return "atelier DevOps pratique (Docker Desktop + labs GitHub Actions)";
        if (List.of("angular","react","vue").contains(skill))
            return "tutoriel officiel du framework, mini-projet portfolio";
        if (List.of("sql","postgresql","mysql","mongodb").contains(skill))
            return "exercices SQLZoo / MongoDB University";
        if (List.of("aws","azure","gcp").contains(skill))
            return "certification Cloud (free tier + labs pratiques)";
        return "documentation officielle + projet concret";
    }

    private String buildBreakdown(double tfidf, double skills, double niveau, double test) {
        String testPart = test >= 0
                ? String.format(",\"test\":%.3f", test)
                : ",\"test\":null";
        return String.format("{\"similarite\":%.3f,\"competences\":%.3f,\"niveau\":%.3f%s}",
                tfidf, skills, niveau, testPart);
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /**
     * Anonymise un texte en remplaçant emails, téléphones et URLs par des marqueurs.
     * Utilisé avant tout envoi vers les layers 1 et 2.
     */
    public String anonymize(String text) {
        if (text == null) return "";
        String result = EMAIL_PATTERN.matcher(text).replaceAll("[EMAIL]");
        result = PHONE_PATTERN.matcher(result).replaceAll("[PHONE]");
        result = URL_PATTERN.matcher(result).replaceAll("[URL]");
        return result;
    }
}
