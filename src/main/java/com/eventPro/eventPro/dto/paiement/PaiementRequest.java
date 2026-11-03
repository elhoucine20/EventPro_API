package com.eventPro.eventPro.dto.paiement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PaiementRequest(

        // TEMPORAIRE : sera remplace par l'utilisateur connecte (Spring Security)
        @NotNull(message = "Le client est obligatoire")
        UUID clientId,

        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0.00", message = "Le montant ne peut pas etre negatif")
        BigDecimal montant,

        @NotBlank(message = "La cle d'idempotence est obligatoire")
        @Size(max = 100, message = "100 caracteres maximum")
        String idempotencyKey,


        // TEMPORAIRE (test uniquement) : forcer un echec de paiement
        Boolean simulerEchec // ?????

) {
}
