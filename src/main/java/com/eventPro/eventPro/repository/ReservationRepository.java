package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.repository.projection.CleQuantiteMontant;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

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



    @Query("""
        select r.id from Reservation r
        where r.event.statut = com.eventPro.eventPro.model.enums.StatusEvenment.CANCELLED
          and r.statut = :statut
        order by r.dateCreation
        """)
    List<UUID> findIdsByEventAnnuleEtStatut(@Param("statut") StatutReservation statut, Pageable pageable);



    // for filter admin
    @Override
    @EntityGraph(attributePaths = {"event", "client", "promotion"})
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);



    // for stats
    @Query("""
        select new com.eventPro.eventPro.repository.projection.CleQuantiteMontant(r.event.id, count(r), sum(r.montantTotal))
        from Reservation r
        where r.event.id in :eventIds
          and r.statut = com.eventPro.eventPro.model.enums.StatutReservation.CONFIRMED
        group by r.event.id
        """)
    List<CleQuantiteMontant> statsVentesParEvent(@Param("eventIds") Collection<UUID> eventIds);


}
