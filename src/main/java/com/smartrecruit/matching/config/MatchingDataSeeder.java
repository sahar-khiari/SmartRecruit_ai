package com.smartrecruit.matching.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrecruit.entity.OffreEmploi;
import com.smartrecruit.entity.Role;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.matching.engine.SkillDictionary;
import com.smartrecruit.matching.entity.CandidatProfile;
import com.smartrecruit.matching.entity.MatchingCritere;
import com.smartrecruit.matching.repository.CandidatProfileRepository;
import com.smartrecruit.matching.repository.MatchingCritereRepository;
import com.smartrecruit.repository.OffreEmploiRepository;
import com.smartrecruit.repository.RoleRepository;
import com.smartrecruit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Données de démonstration du module matching.
 * Crée 3 offres et 8 profils candidats de qualités variées.
 * Ne s'exécute que si aucun profil matching n'existe déjà.
 *
 * Actif uniquement sans profil "test" Spring (pour ne pas polluer les tests unitaires).
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class MatchingDataSeeder implements CommandLineRunner {

    private final CandidatProfileRepository profileRepo;
    private final MatchingCritereRepository  critereRepo;
    private final OffreEmploiRepository      offreRepo;
    private final UserRepository             userRepo;
    private final RoleRepository             roleRepo;
    private final PasswordEncoder            passwordEncoder;
    private final SkillDictionary            skillDict;
    private final ObjectMapper               objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        // Ne seed que si aucun profil matching n'existe
        if (profileRepo.count() > 0) {
            log.info("[Matching] Données de démo déjà présentes — seed ignoré.");
            return;
        }

        log.info("[Matching] Création des données de démonstration...");

        Role candidateRole = roleRepo.findByName(RoleName.CANDIDATE)
                .orElseGet(() -> roleRepo.save(Role.builder().name(RoleName.CANDIDATE).build()));
        Role recruiterRole = roleRepo.findByName(RoleName.RECRUITER)
                .orElseGet(() -> roleRepo.save(Role.builder().name(RoleName.RECRUITER).build()));

        // ── Recruteur de démo ─────────────────────────────────────────────────
        User recruteur = userRepo.findByEmail("recruteur.demo@smartrecruit.local")
                .orElseGet(() -> userRepo.save(User.builder()
                        .firstName("Sophie").lastName("Martin")
                        .email("recruteur.demo@smartrecruit.local")
                        .password(passwordEncoder.encode("Demo123!"))
                        .enabled(true).role(recruiterRole).build()));

        // ── 3 offres d'emploi de démo ─────────────────────────────────────────
        OffreEmploi offre1 = createOrGetOffre(recruteur,
                "Développeur Java Spring Boot",
                "Nous recherchons un développeur Java confirmé maîtrisant Spring Boot, JPA/Hibernate et les APIs REST. " +
                "Vous intégrerez une équipe Agile pour développer des microservices à haute disponibilité.",
                "Développeur Java Spring Boot · Spring Security · JPA · REST · Docker · PostgreSQL · Git · Agile",
                "INTERMEDIAIRE");

        OffreEmploi offre2 = createOrGetOffre(recruteur,
                "Data Engineer Python",
                "Poste de Data Engineer pour construire et maintenir des pipelines de données. " +
                "Stack : Python, Apache Kafka, PostgreSQL, Docker, Spark, AWS S3.",
                "Python · Kafka · Spark · PostgreSQL · Docker · AWS · Airflow · SQL",
                "SENIOR");

        OffreEmploi offre3 = createOrGetOffre(recruteur,
                "Développeur Fullstack Angular/Spring",
                "Développement d'une application web moderne avec Angular 17 côté frontend " +
                "et Spring Boot côté backend. Expérience REST API et CI/CD requise.",
                "Angular · TypeScript · Spring Boot · Java · REST · CI/CD · Docker · Git",
                "JUNIOR");

        // ── Critères pour offre 1 ────────────────────────────────────────────
        createCriteres(offre1.getId(), List.of(
                new CritereSpec("spring boot", 0.35, true),
                new CritereSpec("java",        0.30, true),
                new CritereSpec("jpa",         0.20, false),
                new CritereSpec("docker",      0.15, false)
        ));

        // ── Critères pour offre 2 ────────────────────────────────────────────
        createCriteres(offre2.getId(), List.of(
                new CritereSpec("python",      0.35, true),
                new CritereSpec("sql",         0.25, false),
                new CritereSpec("kafka",       0.20, false),
                new CritereSpec("docker",      0.20, false)
        ));

        // ── 8 candidats de démo ───────────────────────────────────────────────
        createCandidat("Alice", "Dupont", "alice.dupont@demo.local", candidateRole,
                "SENIOR",
                "Développeuse Java senior avec 8 ans d'expérience. Maîtrise Spring Boot, " +
                "Spring Security, JPA/Hibernate, microservices, Docker, Kubernetes. " +
                "Certifiée AWS. Pratique du TDD et des méthodes Agile/Scrum. " +
                "Développement d'APIs REST haute performance avec PostgreSQL et Redis. " +
                "Leadership technique d'une équipe de 5 développeurs.",
                80);

        createCandidat("Baptiste", "Morel", "baptiste.morel@demo.local", candidateRole,
                "INTERMEDIAIRE",
                "Développeur fullstack Angular / Spring Boot depuis 4 ans. " +
                "Compétences : Java, TypeScript, Angular, Spring Boot, REST, Docker, Git. " +
                "Pratique de CI/CD avec GitLab CI. Travail en Agile.",
                72);

        createCandidat("Clara", "Bernard", "clara.bernard@demo.local", candidateRole,
                "JUNIOR",
                "Jeune diplômée en informatique. Stage en développement Java Spring. " +
                "Connaissance de Java, Spring Boot basique, SQL, Git. " +
                "Motivée par l'apprentissage des microservices et Docker.",
                55);

        createCandidat("David", "Petit", "david.petit@demo.local", candidateRole,
                "SENIOR",
                "Data Engineer expérimenté. Spécialisation : Python, Apache Kafka, Spark, " +
                "Airflow, PostgreSQL, AWS S3, Docker. " +
                "Construction de pipelines ETL pour des données à grande échelle. " +
                "Maîtrise de scikit-learn et pandas pour l'analyse exploratoire. " +
                "7 ans d'expérience.",
                88);

        createCandidat("Emma", "Rousseau", "emma.rousseau@demo.local", candidateRole,
                "INTERMEDIAIRE",
                "Développeuse Python / Django. Expérience en développement web backend. " +
                "Compétences : Python, Django, FastAPI, PostgreSQL, Docker, Git, REST. " +
                "Notions de Kafka et AWS.",
                65);

        createCandidat("Félix", "Girard", "felix.girard@demo.local", candidateRole,
                "JUNIOR",
                "Étudiant en Master Informatique. Projets académiques en Java et Angular. " +
                "Pratique de base en Spring Boot, Git, SQL. " +
                "Intérêt pour le DevOps et Docker.",
                48);

        createCandidat("Gabrielle", "Laurent", "gabrielle.laurent@demo.local", candidateRole,
                "SENIOR",
                "Architecte logicielle. Expertise en microservices, architecture hexagonale, " +
                "Java, Spring Boot, Kafka, Docker, Kubernetes. " +
                "Expérience en design patterns et TDD. SQL avancé, Redis. " +
                "Coach technique en équipes Agile.",
                91);

        createCandidat("Hugo", "Simon", "hugo.simon@demo.local", candidateRole,
                "INTERMEDIAIRE",
                "Développeur mobile reconverti au fullstack web. " +
                "Bonne maîtrise de TypeScript, Angular, React. " +
                "Connaissances backend limitées : Node.js, quelques projets Spring Boot. " +
                "Git, CI/CD basique.",
                60);

        log.info("[Matching] Données de démonstration créées avec succès.");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private OffreEmploi createOrGetOffre(User recruteur, String titre,
                                          String description, String competences, String niveau) {
        return offreRepo.findAll().stream()
                .filter(o -> o.getTitrePoste().equals(titre))
                .findFirst()
                .orElseGet(() -> offreRepo.save(OffreEmploi.builder()
                        .titrePoste(titre)
                        .description(description)
                        .competencesRequises(competences)
                        .niveauRequis(niveau)
                        .typeContrat("CDI")
                        .localisation("Tunis, Tunisie")
                        .active(true)
                        .recruteur(recruteur)
                        .build()));
    }

    private void createCriteres(Long offreId, List<CritereSpec> specs) {
        if (!critereRepo.findByOffreId(offreId).isEmpty()) return;
        for (CritereSpec spec : specs) {
            critereRepo.save(MatchingCritere.builder()
                    .offreId(offreId)
                    .nom(spec.nom())
                    .poids(spec.poids())
                    .obligatoire(spec.obligatoire())
                    .build());
        }
    }

    private void createCandidat(String prenom, String nom, String email,
                                 Role role, String niveau, String cvText, int scoreIa) {
        User user = userRepo.findByEmail(email)
                .orElseGet(() -> userRepo.save(User.builder()
                        .firstName(prenom).lastName(nom)
                        .email(email)
                        .password(passwordEncoder.encode("Demo123!"))
                        .enabled(true).role(role).build()));

        if (!profileRepo.existsByUserId(user.getId())) {
            Set<String> skills = skillDict.extractSkills(cvText);
            String skillsJson;
            try {
                skillsJson = objectMapper.writeValueAsString(new ArrayList<>(skills));
            } catch (Exception e) {
                skillsJson = "[]";
            }
            profileRepo.save(CandidatProfile.builder()
                    .userId(user.getId())
                    .cvText(cvText)
                    .skillsJson(skillsJson)
                    .niveau(niveau)
                    .resumeProfessionnel(prenom + " " + nom + " — " + niveau)
                    .build());
        }
    }

    private record CritereSpec(String nom, double poids, boolean obligatoire) {}
}
