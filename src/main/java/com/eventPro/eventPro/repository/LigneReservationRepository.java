package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.LigneReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface LigneReservationRepository extends JpaRepository<LigneReservation, UUID> {

    //calculer le nombre de billets que deja confirmer ou deja tomporerment reservee pour une categorie donnees
    @Query(" select coalesce(sum(l.quantite), 0L) from LigneReservation l " +

            "where l.categorieBillet.id = :categorieId " +
            "and (l.reservation.statut = com.eventPro.eventPro.model.enums.StatutReservation.CONFIRMED " +

            // reserver et paiment pas encours effectue
            "or (l.reservation.statut = com.eventPro.eventPro.model.enums.StatutReservation.PENDING_PAYMENT " +
            "and l.reservation.dateExpiration > :now)) ")
    long sumQuantiteActive(@Param("categorieId") UUID categorieId,
                           @Param("now") LocalDateTime now);

    @Query("""
            select coalesce(sum(l.quantite), 0L) from LigneReservation l
                        
            where l.categorieBillet.id = :categorieId
              and l.reservation.client.id = :clientId
              and (l.reservation.statut = com.eventPro.eventPro.model.enums.StatutReservation.CONFIRMED
                          
                   or (l.reservation.statut = com.eventPro.eventPro.model.enums.StatutReservation.PENDING_PAYMENT
                       and l.reservation.dateExpiration > :now))
            """)
    long sumQuantiteActiveByClient(@Param("categorieId") UUID categorieId,
                                   @Param("clientId") UUID clientId,
                                   @Param("now") LocalDateTime now);


}
