package com.eventPro.eventPro.dto.stats;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrganisateurStatsResponse(
        UUID organisateurId,
        String nomOrganisation,
        long totalBilletsVendus,
        BigDecimal totalChiffreAffaires,
        BigDecimal totalRembourse,
        List<EventStatsResponse> events
) {}