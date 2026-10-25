package com.smartrecruit.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Requête de mise à jour d'une candidature (typiquement par le service IA).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandidatureRequest {

    private Integer scoreIa;
    private String recommandationIa;
    private String feedbackIa;
    private String pointsForts;
    private String pointsFaibles;
    private String testId;
}
