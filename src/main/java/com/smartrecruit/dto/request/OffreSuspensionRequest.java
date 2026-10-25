package com.smartrecruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motif obligatoire lorsqu'un administrateur suspend une offre.
 */
public record OffreSuspensionRequest(
        @NotBlank(message = "Le motif de suspension est obligatoire")
        @Size(max = 500)
        String motif
) {
}
