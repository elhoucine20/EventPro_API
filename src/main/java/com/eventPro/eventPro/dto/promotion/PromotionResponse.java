package com.eventPro.eventPro.dto.promotion;

import com.eventPro.eventPro.model.enums.TypeReduction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PromotionResponse(
        UUID id,
        String code,
        TypeReduction typeReduction,
        BigDecimal valeurReduction,
        LocalDate dateDebut,
        LocalDate dateFin,
        Integer maxUtilisations,
        Integer nombreUtilisations,
        UUID eventId

) {
}
