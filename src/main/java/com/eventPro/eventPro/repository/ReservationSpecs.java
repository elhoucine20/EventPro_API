package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.enums.StatutReservation;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class ReservationSpecs {

    private ReservationSpecs(){}


    public static Specification<Reservation> statutEgal(StatutReservation statut) {
        return (root, query, cb) -> statut == null ? null : cb.equal(root.get("statut"), statut);
    }

    public static Specification<Reservation> eventEgal(UUID eventId) {
        return (root, query, cb) -> eventId == null ? null : cb.equal(root.get("event").get("id"), eventId);
    }

    public static Specification<Reservation> clientEgal(UUID clientId) {
        return (root, query, cb) -> clientId == null ? null : cb.equal(root.get("client").get("id"), clientId);
    }

}
