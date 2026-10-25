package com.smartrecruit.matching;

import com.smartrecruit.matching.engine.SkillDictionary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du dictionnaire de compétences.
 * Aucune dépendance externe : 100 % autonome.
 */
@DisplayName("SkillDictionary — extraction et normalisation")
class SkillDictionaryTest {

    private SkillDictionary dict;

    @BeforeEach
    void setUp() {
        dict = new SkillDictionary();
    }

    @Test
    @DisplayName("Extrait 'java' depuis un texte simple")
    void extractsJava() {
        Set<String> skills = dict.extractSkills("Développeur Java confirmé");
        assertThat(skills).contains("java");
    }

    @Test
    @DisplayName("Alias 'springboot' → canonique 'spring boot'")
    void aliasSpringBoot() {
        Set<String> skills = dict.extractSkills("Expérience avec springboot et docker");
        assertThat(skills).contains("spring boot");
        assertThat(skills).contains("docker");
    }

    @Test
    @DisplayName("Alias 'js' → canonique 'javascript'")
    void aliasJs() {
        Set<String> skills = dict.extractSkills("Développeur js frontend");
        assertThat(skills).contains("javascript");
    }

    @Test
    @DisplayName("Alias 'postgres' → canonique 'postgresql'")
    void aliasPostgres() {
        Set<String> skills = dict.extractSkills("Base de données postgres");
        assertThat(skills).contains("postgresql");
    }

    @Test
    @DisplayName("Texte null → ensemble vide")
    void nullText() {
        assertThat(dict.extractSkills(null)).isEmpty();
    }

    @Test
    @DisplayName("Texte vide → ensemble vide")
    void emptyText() {
        assertThat(dict.extractSkills("")).isEmpty();
    }

    @Test
    @DisplayName("canonical() retrouve la forme canonique d'un alias")
    void canonical() {
        assertThat(dict.canonical("k8s")).isEqualTo("kubernetes");
        assertThat(dict.canonical("sklearn")).isEqualTo("scikit-learn");
    }

    @Test
    @DisplayName("Insensible à la casse")
    void caseInsensitive() {
        Set<String> skills = dict.extractSkills("Maîtrise de DOCKER et KUBERNETES");
        assertThat(skills).contains("docker");
        assertThat(skills).contains("kubernetes");
    }
}
