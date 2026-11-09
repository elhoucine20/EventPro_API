package com.eventPro.eventPro.dto.ticket;

import com.eventPro.eventPro.model.enums.StatutBillet;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record TicketResponse(
        UUID id,
        String code,
        StatutBillet statut,
        LocalDateTime dateEmission,
        LocalDateTime dateControle,
        String categorieNom,
        UUID eventId,
        String eventTitre,
        String eventLieu,
        LocalDateTime eventDateDebut,
        String reservationReference,
        String porteur

) {
}
