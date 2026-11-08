package com.eventPro.eventPro.dto.stats;

import java.math.BigDecimal;
import java.util.UUID;

public record CategorieStats(
        UUID categorieId,
        String nom,
        Integer quantiteTotale,
        long billetsVendus,
        BigDecimal chiffreAffaires
) {}