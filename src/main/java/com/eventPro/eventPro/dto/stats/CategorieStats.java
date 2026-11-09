package com.eventPro.eventPro.dto.stats;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CategorieStats(
        UUID categorieId,
        String nom,
        Integer quantiteTotale,
        long billetsVendus,
        BigDecimal chiffreAffaires
) {}