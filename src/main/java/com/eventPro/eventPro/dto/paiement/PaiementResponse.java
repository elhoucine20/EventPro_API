package com.eventPro.eventPro.dto.paiement;

import com.eventPro.eventPro.model.enums.StatutPaiement;
import com.eventPro.eventPro.model.enums.StatutReservation;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PaiementResponse(

        UUID id,
        String reference,
        BigDecimal montant,
        LocalDateTime datePaiement,
        StatutPaiement statut,
        UUID reservationId,
        StatutReservation reservationStatut

) {
}
