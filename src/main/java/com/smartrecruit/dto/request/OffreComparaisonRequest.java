package com.smartrecruit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Module 2 : comparaison IA de 2 ou 3 offres par un candidat.
 */
@Schema(description = "Offres à comparer et profil facultatif du candidat (anonymisé avant l'envoi à l'IA)")
public record OffreComparaisonRequest(

        @Schema(description = "Identifiants des offres (2 ou 3, différentes)", example = "[1, 2]")
        @NotNull(message = "Choisissez les offres à comparer")
        @Size(min = 2, max = 3, message = "Comparez 2 ou 3 offres")
        List<@NotNull Long> offreIds,

        @Schema(description = "Profil en quelques mots (facultatif)",
                example = "Junior, je connais Java et Spring, un peu d'Angular")
        @Size(max = 500, message = "Le profil ne doit pas dépasser 500 caractères")
        String profil
) {
}
