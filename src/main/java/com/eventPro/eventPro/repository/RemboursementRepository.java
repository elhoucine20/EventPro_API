package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Remboursement;
import com.eventPro.eventPro.model.enums.StatutRemboursement;
import com.eventPro.eventPro.repository.projection.CleMontant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemboursementRepository extends JpaRepository<Remboursement, UUID> {

    Optional<Remboursement> findFirstByPaiementIdAndStatut(UUID paiementId, StatutRemboursement statut);

    List<Remboursement> findByPaiementReservationIdOrderByDateDemandeDesc(UUID reservationId);

    Page<Remboursement> findByStatut(StatutRemboursement statu, Pageable pageable);


    // remboursements APPROVED pour chaque event
    @Query("""
        select new com.eventPro.eventPro.repository.projection.CleMontant(rb.paiement.reservation.event.id, sum(rb.montant))
        from Remboursement rb
        where rb.paiement.reservation.event.id in :eventIds
          and rb.statut = com.eventPro.eventPro.model.enums.StatutRemboursement.APPROVED
        group by rb.paiement.reservation.event.id
        """)
    List<CleMontant> sumMontantApprouveParEvent(@Param("eventIds") Collection<UUID> eventIds);
}
