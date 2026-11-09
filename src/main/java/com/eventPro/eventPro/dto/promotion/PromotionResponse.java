package com.eventPro.eventPro.dto.promotion;

import com.eventPro.eventPro.model.enums.TypeReduction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
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
