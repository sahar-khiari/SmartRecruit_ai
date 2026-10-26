package com.smartrecruit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Module 2 : résultat de la comparaison IA (Cohere). Niveau, contrat et lieu viennent des offres en base ;
 * missions, compétences clés, points forts et recommandation sont proposés par l'IA.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Comparaison IA d'offres")
public record OffreComparaisonResponse(
        List<OffreComparee> offres,
        Recommandation recommandation,
        @Schema(description = "true si le profil du candidat a été pris en compte") boolean profilUtilise,
        @Schema(example = "command-r-08-2024") String modele
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OffreComparee(
            Long offreId,
            String titrePoste,
            String missions,
            String niveau,
            List<String> competencesCles,
            String contrat,
            String lieu,
            List<String> pointsForts,
            String profilIdeal
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommandation(
            @Schema(description = "Offre recommandée, null si aucune ne se détache ou sans profil") Long offreId,
            String justification
    ) {
    }
}
