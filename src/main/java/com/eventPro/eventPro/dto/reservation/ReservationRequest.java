package com.eventPro.eventPro.dto.reservation;

import com.eventPro.eventPro.dto.ligneReservation.LigneReservationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ReservationRequest(

        // TEMPORAIRE : sera remplacé par l'utilisateur connecté (Spring Security)
        @NotNull(message = "Le client est obligatoire")
        UUID clientId,

        @Size(max = 50, message = "50 caracteres maximum")
        String promoCode,

        @NotEmpty(message = "Au moins une ligne est obligatoire")
        @Size(max = 10, message = "10 lignes maximum")
        @Valid
        List<LigneReservationRequest> lignes

) {
    public ReservationRequest{
        promoCode = (promoCode == null || promoCode.isBlank()) ? null : promoCode.strip().toUpperCase();
    }
}
