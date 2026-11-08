package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.LigneReservation;
import com.eventPro.eventPro.repository.projection.CleQuantite;
import com.eventPro.eventPro.repository.projection.CleQuantiteMontant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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



    // for stats
       // billles ventr de chaque event
        @Query("""
            select new com.eventPro.eventPro.repository.projection.CleQuantite(l.reservation.event.id, sum(l.quantite))
            from LigneReservation l
            where l.reservation.event.id in :eventIds
              and l.reservation.statut = com.eventPro.eventPro.model.enums.StatutReservation.CONFIRMED
            group by l.reservation.event.id
            """)
        List<CleQuantite> sumBilletsVendusParEvent(@Param("eventIds") Collection<UUID> eventIds);

    // billles ventr de chaque categorie + montant avant la promo
    @Query("""
            select new com.eventPro.eventPro.repository.projection.CleQuantiteMontant(l.categorieBillet.id, sum(l.quantite), sum(l.sousTotal))
            from LigneReservation l
            where l.categorieBillet.event.id = :eventId
              and l.reservation.statut = com.eventPro.eventPro.model.enums.StatutReservation.CONFIRMED
            group by l.categorieBillet.id
            """)
        List<CleQuantiteMontant> sumVentesParCategorie(@Param("eventId") UUID eventId);

}
