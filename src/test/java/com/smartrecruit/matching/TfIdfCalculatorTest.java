package com.smartrecruit.matching;

import com.smartrecruit.matching.engine.TfIdfCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du calculateur TF-IDF.
 * Valide la formule mathématique sans dépendance externe.
 */
@DisplayName("TfIdfCalculator — similarité cosinus")
class TfIdfCalculatorTest {

    private TfIdfCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TfIdfCalculator();
    }

    @Test
    @DisplayName("Score maximal pour des textes identiques")
    void identicalTexts() {
        String text = "Java Spring Boot développeur backend microservices";
        double score = calculator.similarity(text, text);
        assertThat(score).isGreaterThan(0.99);
    }

    @Test
    @DisplayName("Score minimal pour des textes sans terme commun")
    void noCommonTerms() {
        double score = calculator.similarity("java spring", "python pandas");
        assertThat(score).isLessThan(0.05);
    }

    @Test
    @DisplayName("Score intermédiaire pour des textes partiellement similaires")
    void partialOverlap() {
        double score = calculator.similarity(
                "Java Spring Boot Docker PostgreSQL",
                "Java backend développeur REST API Docker");
        assertThat(score).isBetween(0.1, 0.9);
    }

    @Test
    @DisplayName("Score nul pour texte null")
    void nullInput() {
        assertThat(calculator.similarity(null, "Java")).isEqualTo(0.0);
        assertThat(calculator.similarity("Java", null)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Score nul pour texte vide")
    void emptyInput() {
        assertThat(calculator.similarity("", "Java")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Résultat dans [0, 1]")
    void scoreInRange() {
        double score = calculator.similarity(
                "Développeur Angular TypeScript RxJS Material Design frontend",
                "Frontend engineer Angular React TypeScript CSS animations");
        assertThat(score).isBetween(0.0, 1.0);
    }
}
