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

import java.time.LocalDateTime;
import java.util.List;
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


    // les reservatioon que doit changer statu vers EXPIRED
    @Query("""
        select r.id from Reservation r
        where r.statut = com.eventPro.eventPro.model.enums.StatutReservation.PENDING_PAYMENT
          and r.dateExpiration < :now
        order by r.dateExpiration
        """)
    List<UUID> findExpiredIds(@Param("now") LocalDateTime now, Pageable pageable);
}
