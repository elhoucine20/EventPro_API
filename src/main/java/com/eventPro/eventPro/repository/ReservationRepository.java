package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @EntityGraph(attributePaths = "event")
    Page<Reservation> findByClientId(UUID clientId, Pageable pageable);

    boolean existsByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)  // ????
    @Query("select r from Reservation r where r.id = :id ")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);
}
