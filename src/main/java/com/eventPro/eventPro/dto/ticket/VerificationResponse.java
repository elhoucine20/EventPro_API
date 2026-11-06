package com.eventPro.eventPro.dto.ticket;

public record VerificationResponse(

        boolean accesAutorise,
        String message,
        TicketResponse billet
) {
}
