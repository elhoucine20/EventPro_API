package com.eventPro.eventPro.dto.paiement;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaiementRequest(

        // TEMPORAIRE : sera remplace par l'utilisateur connecte (Spring Security)
        @NotNull(message = "Le client est obligatoire")
        UUID clientId,

        // TEMPORAIRE (test uniquement) : forcer un echec de paiement
        Boolean simulerEchec // ?????

) {
}
