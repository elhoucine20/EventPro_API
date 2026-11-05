package com.eventPro.eventPro.repository.projection;

import java.util.UUID;

public record CleQuantite(
        UUID cle,   // id de categorie ou event selon query
        Long quantite
) {
}
