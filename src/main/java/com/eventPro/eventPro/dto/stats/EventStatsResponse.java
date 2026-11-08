package com.eventPro.eventPro.dto.stats;

import com.eventPro.eventPro.model.enums.StatusEvenment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EventStatsResponse(
        UUID eventId,
        String titre,
        StatusEvenment statut,
        long reservationsConfirmees,
        long billetsVendus,
        long capaciteTotale,
        BigDecimal tauxRemplissage,
        BigDecimal chiffreAffaires,
        BigDecimal montantRembourse,
        List<CategorieStats> categories
) {}