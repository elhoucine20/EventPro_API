package com.eventPro.eventPro.dto.ticket;

import lombok.Builder;

@Builder
public record VerificationResponse(

        boolean accesAutorise,
        String message,
        TicketResponse billet
) {
}
