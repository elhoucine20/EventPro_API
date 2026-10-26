package com.eventPro.eventPro.dto.categorie;

import java.math.BigDecimal;
import java.util.UUID;

public record CategorieBilletResponse(
        UUID id,
        String nom,
        BigDecimal prixUnitaire,
        Integer quantiteTotale,
        Integer quotaMaxParClient,
        UUID eventId
) {
}
