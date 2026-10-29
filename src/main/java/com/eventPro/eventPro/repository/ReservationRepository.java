package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @EntityGraph(attributePaths = "event")
    Page<Reservation> findByClientId(UUID clientId, Pageable pageable);

    boolean existsByReference(String reference);
}
