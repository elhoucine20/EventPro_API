package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Remboursement;
import com.eventPro.eventPro.model.enums.StatutRemboursement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemboursementRepository extends JpaRepository<Remboursement, UUID> {

    Optional<Remboursement> findFirstByPaiementIdAndStatut(UUID paiementId, StatutRemboursement statut);

    List<Remboursement> findByPaiementReservationIdOrderByDateDemandeDesc(UUID reservationId);

}
