package com.eventPro.eventPro.dto.event;

import com.eventPro.eventPro.model.enums.StatusEvenment;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String titre,
        String description,
        String lieu,
        LocalDateTime dateDebut,
        LocalDateTime dateFin,
        LocalDateTime dateOuvertureVente,
        LocalDateTime dateClotureVente,
        StatusEvenment statut,
        UUID organisateurId,
        String nomOrganisation
) {
}
