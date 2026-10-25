package com.smartrecruit.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Module 2 : donne un statut aux offres créées avant l'ajout du cycle de vie
 * (active = true -> PUBLIEE, sinon CLOTUREE). Idempotent, s'exécute à chaque démarrage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OffreStatutMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int updated = jdbcTemplate.update(
                "UPDATE offres_emploi SET statut = CASE WHEN active THEN 'PUBLIEE' ELSE 'CLOTUREE' END "
                        + "WHERE statut IS NULL");
        if (updated > 0) {
            log.info("Module 2 : statut initialisé pour {} offre(s) existante(s)", updated);
        }
    }
}
