package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Billet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BilletRepository extends JpaRepository<Billet, UUID> {

    List<Billet> findByLigneReservationReservationId(UUID reservationId);// romborcementy



    // for agent ticket
    Optional<Billet> findByCode(String code);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Billet b
        set b.statut = com.eventPro.eventPro.model.enums.StatutBillet.USED, b.dateControle = :now
        where b.code = :code
          and b.statut = com.eventPro.eventPro.model.enums.StatutBillet.VALID
        """)
    int composter(@Param("code") String code, @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = {
            "ligneReservation",
            "ligneReservation.categorieBillet",
            "ligneReservation.reservation",
            "ligneReservation.reservation.event",
            "ligneReservation.reservation.client"
    })
    Page<Billet> findByLigneReservationReservationClientId(UUID clientId, Pageable pageable);


}
