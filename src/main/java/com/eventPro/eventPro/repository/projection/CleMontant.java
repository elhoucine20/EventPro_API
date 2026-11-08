package com.eventPro.eventPro.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public record CleMontant(
        UUID cle,
        BigDecimal montant
){
}
