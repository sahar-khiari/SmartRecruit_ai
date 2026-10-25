package com.smartrecruit.matching;

import com.smartrecruit.matching.engine.JavaMatchingEngine;
import com.smartrecruit.matching.engine.MatchingResultLayer0;
import com.smartrecruit.matching.engine.SkillDictionary;
import com.smartrecruit.matching.engine.TfIdfCalculator;
import com.smartrecruit.matching.entity.MatchingCritere;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du moteur Java (Layer 0).
 * Valide les formules de fusion, le verdict et le filtre dur.
 */
@DisplayName("JavaMatchingEngine — Layer 0")
class JavaMatchingEngineTest {

    private JavaMatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new JavaMatchingEngine(new TfIdfCalculator(), new SkillDictionary());
    }

    @Test
    @DisplayName("Profil parfait → STRONG_MATCH")
    void strongMatch() {
        String cv = "Développeur Java Spring Boot senior. Maîtrise JPA, Docker, PostgreSQL, " +
                    "Kubernetes, microservices, CI/CD, Git, Agile. 8 ans d'expérience.";
        String offre = "Développeur Java Spring Boot. Compétences requises : Java, Spring Boot, " +
                       "JPA, Docker, PostgreSQL, Agile.";

        MatchingResultLayer0 result = engine.compute(cv, offre, "SENIOR", "INTERMEDIAIRE",
                85, List.of());

        assertThat(result.verdict()).isEqualTo("STRONG_MATCH");
        assertThat(result.finalScore()).isGreaterThan(0.6);
    }

    @Test
    @DisplayName("Profil inadéquat → WEAK")
    void weakMatch() {
        String cv = "Designer graphique. Maîtrise Photoshop, Illustrator, InDesign. Créatif.";
        String offre = "Développeur Java Spring Boot. Java, Spring Boot, JPA, Docker requis.";

        MatchingResultLayer0 result = engine.compute(cv, offre, "JUNIOR", "SENIOR", null, List.of());

        assertThat(result.verdict()).isEqualTo("WEAK");
        assertThat(result.finalScore()).isLessThan(0.4);
    }

    @Test
    @DisplayName("Critère obligatoire absent → score = 0")
    void mandatoryCriteriaFilter() {
        String cv = "Développeur Python Django. Maîtrise Flask et SQL.";
        String offre = "Développeur Java Spring Boot requis.";

        MatchingCritere critereJava = MatchingCritere.builder()
                .nom("java").poids(0.5).obligatoire(true).build();

        MatchingResultLayer0 result = engine.compute(cv, offre, "JUNIOR", "JUNIOR",
                null, List.of(critereJava));

        assertThat(result.finalScore()).isEqualTo(0.0);
        assertThat(result.verdict()).isEqualTo("WEAK");
    }

    @Test
    @DisplayName("Score test absent → renormalisation des poids")
    void noTestScore() {
        String cv = "Développeur Java Spring Boot. Docker, Git.";
        String offre = "Développeur Java Spring Boot.";

        MatchingResultLayer0 result = engine.compute(cv, offre, "INTERMEDIAIRE", "INTERMEDIAIRE",
                null, List.of());

        assertThat(result.testScore()).isEqualTo(-1.0);
        assertThat(result.finalScore()).isGreaterThan(0.0);
        assertThat(result.finalScore()).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("Anonymisation : email et téléphone masqués")
    void anonymization() {
        String anonymized = engine.anonymize(
                "Contact : jean.dupont@email.com, tel 06 12 34 56 78");
        assertThat(anonymized).contains("[EMAIL]");
        assertThat(anonymized).contains("[PHONE]");
        assertThat(anonymized).doesNotContain("jean.dupont@email.com");
        assertThat(anonymized).doesNotContain("06 12 34 56 78");
    }

    @Test
    @DisplayName("Explication non vide pour tout résultat")
    void explanationNotEmpty() {
        MatchingResultLayer0 result = engine.compute(
                "Développeur Java", "Java Spring Boot", "JUNIOR", "JUNIOR", null, List.of());
        assertThat(result.explication()).isNotBlank();
    }

    @Test
    @DisplayName("Résultat cohérent : missingSkills ∩ matchedSkills = ∅")
    void noSkillOverlap() {
        String cv = "Java Spring Boot Docker";
        String offre = "Java Spring Boot Kubernetes PostgreSQL";
        MatchingResultLayer0 result = engine.compute(cv, offre, "INTERMEDIAIRE", "INTERMEDIAIRE",
                null, List.of());
        assertThat(result.matchedSkills()).doesNotContainAnyElementsOf(result.missingSkills());
    }
}
