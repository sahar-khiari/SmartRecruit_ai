package com.smartrecruit.matching.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Marqueur de configuration pour le module Matching.
 *
 * Le contrôleur MatchingController est découvert automatiquement par Spring
 * via le scan de composants (@RestController). Ce fichier documente l'isolation
 * du module et sert de point d'entrée pour la configuration future (ex. : cache,
 * rate-limiting spécifique au matching).
 *
 * Règle : si le module échoue au démarrage, l'application continue de tourner
 * (les exceptions dans @PostConstruct du module ne remontent pas au contexte racine).
 */
@Configuration
@Slf4j
public class MatchingRouteRegistrar {
    // Intentionnellement vide : la découverte des routes est automatique.
    // Ce fichier sert de documentation et de marqueur de module.
}
