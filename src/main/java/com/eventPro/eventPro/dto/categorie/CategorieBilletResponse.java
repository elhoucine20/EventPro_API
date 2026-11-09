package com.eventPro.eventPro.dto.categorie;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CategorieBilletResponse(
        UUID id,
        String nom,
        BigDecimal prixUnitaire,
        Integer quantiteTotale,
        Integer quotaMaxParClient,
        UUID eventId,
        Integer placesDisponibles
) {
}
