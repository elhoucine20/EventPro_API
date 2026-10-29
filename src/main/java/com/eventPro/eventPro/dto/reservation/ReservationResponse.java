package com.eventPro.eventPro.dto.reservation;

import com.eventPro.eventPro.dto.ligneReservation.LigneReservationResponse;
import com.eventPro.eventPro.model.enums.StatutReservation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(

        UUID id,
        String reference,
        StatutReservation statut,
        LocalDateTime dateCreation,
        LocalDateTime dateExpiration,
        BigDecimal montantTotal,
        String promoCode,
        UUID eventId,
        String eventTitre,
        UUID clientId,
        List<LigneReservationResponse> lignes
) {
}


