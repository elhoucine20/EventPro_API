package com.eventPro.eventPro.dto.remboursement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RemboursementRequest(
        // TEMPORAIRE : sera remplacé par l'utilisateur connecté (Spring Security)
        @NotNull(message = "Le client est obligatoire")
        UUID clientId,

        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 255, message = "255 caractères maximum")
        String motif

) {
}
