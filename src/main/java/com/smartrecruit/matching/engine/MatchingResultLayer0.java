package com.smartrecruit.matching.engine;

import java.util.List;
import java.util.Set;

/**
 * Résultat intermédiaire produit par le moteur Java (Layer 0).
 * Immuable, sérialisable en JSON par Jackson.
 */
public record MatchingResultLayer0(

        /** Score final fusionné (0–1). */
        double finalScore,

        /** Composante similarité TF-IDF (0–1). */
        double semanticScore,

        /** Taux de couverture des compétences requises (0–1). */
        double skillsCoverageScore,

        /** Adéquation du niveau d'expérience (0–1). */
        double experienceScore,

        /** Score du test technique normalisé (0–1), -1.0 si absent. */
        double testScore,

        /** Verdict : STRONG_MATCH | POSSIBLE | WEAK */
        String verdict,

        /** Compétences demandées présentes dans le CV. */
        Set<String> matchedSkills,

        /** Compétences demandées absentes du CV. */
        Set<String> missingSkills,

        /** Explication textuelle destinée au recruteur. */
        String explication,

        /** JSON du détail des composantes pour stockage en base. */
        String breakdownJson
) {}
