package com.eventPro.eventPro.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public record CleQuantiteMontant(

        UUID cle,
        Long quantite,
        BigDecimal montant
) {
}
