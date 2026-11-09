package com.eventPro.eventPro.dto.remboursement;

import com.eventPro.eventPro.model.enums.StatutRemboursement;
import com.eventPro.eventPro.model.enums.StatutReservation;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record RemboursementResponse(
        UUID id,
        BigDecimal montant,
        String motif,
        String decision,
        LocalDateTime dateDemande,
        StatutRemboursement statut,
        UUID reservationId,
        StatutReservation reservationStatut,
        String paiementReference
) {}