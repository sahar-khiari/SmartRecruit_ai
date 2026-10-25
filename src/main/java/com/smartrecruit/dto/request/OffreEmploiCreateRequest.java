package com.smartrecruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Requête de création ou mise à jour d'une offre d'emploi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OffreEmploiCreateRequest {

    @NotBlank
    @Size(max = 200)
    private String titrePoste;

    @NotBlank
    private String description;

    @NotBlank
    @Size(max = 100)
    private String niveauRequis;

    @NotBlank
    private String competencesRequises;

    @Size(max = 200)
    private String localisation;

    @Size(max = 50)
    private String typeContrat;

    private int dureeTestMinutes = 30;
}
