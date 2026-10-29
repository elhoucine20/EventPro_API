package com.eventPro.eventPro.dto.ligneReservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record LigneReservationResponse(
        UUID id,
        UUID categorieBilletId,
        String categorieNom,
        Integer quantite,
        BigDecimal prixUnitaire,
        BigDecimal sousTotal

) {
}
