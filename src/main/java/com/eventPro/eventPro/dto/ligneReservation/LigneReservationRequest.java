package com.eventPro.eventPro.dto.ligneReservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LigneReservationRequest(

        @NotNull(message = "categorie est obligatoire")
        UUID categorieBilletId,

        @NotNull(message = "la quantite est obligatoire")
        @Min(value = 1,message = "la quantite doit etre au moins 1")
        Integer quantite
) {
}
