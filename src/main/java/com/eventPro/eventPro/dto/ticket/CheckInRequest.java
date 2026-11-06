package com.eventPro.eventPro.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CheckInRequest(

        // TEMPORAIRE : sera remplacé par l'utilisateur connecté (Spring Security)
        @NotNull(message = "L'agent est obligatoire")
        UUID agentId,

        @NotBlank(message = "Le code du billet est obligatoire")
        @Size(max = 100, message = "100 caracteres maximum")
        String code
) {
    public CheckInRequest {
        code = code == null ? null : code.strip().toUpperCase();
    }
}