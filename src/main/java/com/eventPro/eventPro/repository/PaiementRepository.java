package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public interface PaiementRepository extends JpaRepository<Paiement,UUID> {

    Optional<Paiement> findByReservationId(UUID reservationId); // optionel car possible expire reservation
    boolean existsByReference(String reference); // est-ce que reference deja exist

    Optional<Paiement> findByIdempotencyKey(String idempotencyKey);
}
