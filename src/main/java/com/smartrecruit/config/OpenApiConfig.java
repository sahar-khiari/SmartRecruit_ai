package com.smartrecruit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration springdoc-openapi / Swagger UI.
 * Swagger UI : http://localhost:8080/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI smartRecruitOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartRecruit AI - API Backend")
                        .description("""
                                API REST de SmartRecruit AI : gestion des utilisateurs, inscription,
                                authentification JWT, autorisation par rôles (ADMIN, RECRUITER, CANDIDATE),
                                profil utilisateur, administration des comptes, conversations privées avec
                                l'Assistant RH et base de connaissances RH.

                                **Authentification** : appelez POST /api/auth/login, copiez la valeur de
                                `accessToken`, cliquez sur Authorize et saisissez uniquement le token
                                (sans le préfixe « Bearer »). Swagger ajoute automatiquement l'en-tête
                                `Authorization: Bearer <token>`.

                                **Assistant RH - phase 1** : conversations, messages USER et base de
                                connaissances. Aucune réponse IA, aucun RAG ni LLM n'est encore appelé.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Équipe SmartRecruit AI")
                                .email("contact@smartrecruit.local"))
                        .license(new License()
                                .name("Projet universitaire - usage pédagogique")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Développement local")
                ))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .description("JWT obtenu via POST /api/auth/login. "
                                        + "Saisir uniquement le token, sans le préfixe 'Bearer '.")));
    }
}
