package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.remboursement.RemboursementRequest;
import com.eventPro.eventPro.dto.remboursement.RemboursementResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.remboursement.RemboursementMapper;
import com.eventPro.eventPro.model.Billet;
import com.eventPro.eventPro.model.Paiement;
import com.eventPro.eventPro.model.Remboursement;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.model.enums.StatutBillet;
import com.eventPro.eventPro.model.enums.StatutRemboursement;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.paiement.PaymentGateway;
import com.eventPro.eventPro.paiement.PaymentResult;
import com.eventPro.eventPro.paiement.PaymentGateway;
import com.eventPro.eventPro.paiement.PaymentResult;
import com.eventPro.eventPro.repository.BilletRepository;
import com.eventPro.eventPro.repository.PaiementRepository;
import com.eventPro.eventPro.repository.PromotionRepository;
import com.eventPro.eventPro.repository.RemboursementRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RemboursementService {

    private static final int DELAI_REMBOURSEMENT_HEURES = 48;

    private final RemboursementRepository remboursementRepository;
    private final ReservationRepository reservationRepository;
    private final PaiementRepository paiementRepository;
    private final BilletRepository billetRepository;
    private final PromotionRepository promotionRepository;
    private final PaymentGateway paymentGateway;
    private final RemboursementMapper remboursementMapper;

    // les méthodes ci-dessous
    @Transactional
    public RemboursementResponse demander(UUID reservationId, RemboursementRequest request) {
        LocalDateTime now = LocalDateTime.now();

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation introuvable : " + reservationId));
        checkOwner(reservation, request.clientId());
        checkRemboursable(reservation);

        Paiement paiement = paiementRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun paiement pour cette réservation"));

        Optional<Remboursement> dejaRefuse = remboursementRepository
                .findFirstByPaiementIdAndStatut(paiement.getId(), StatutRemboursement.REJECTED);
        if (dejaRefuse.isPresent()) {
            return remboursementMapper.toResponse(dejaRefuse.get());
        }

        List<Billet> billets = billetRepository.findByLigneReservationReservationId(reservationId);
        String raisonRefus = raisonRefus(reservation, billets, now);

        Remboursement remboursement = Remboursement.builder()
                .paiement(paiement)
                .motif(request.motif())
                .dateDemande(now)
                .statut(StatutRemboursement.PENDING)
                .build();

        if (raisonRefus != null) {
            remboursement.setMontant(BigDecimal.ZERO);
            remboursement.setDecision(raisonRefus);
            remboursement.refuser();
        } else {
            remboursement.setMontant(paiement.getMontant());
            remboursement.setDecision("Remboursement intégral (100%)");
            executerRemboursement(reservation, paiement, remboursement, billets);
        }

        return remboursementMapper.toResponse(remboursementRepository.save(remboursement));
    }
    @Transactional(readOnly = true)
    public List<RemboursementResponse> getByReservation(UUID reservationId, UUID clientId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation introuvable : " + reservationId));
        checkOwner(reservation, clientId);
        return remboursementRepository.findByPaiementReservationIdOrderByDateDemandeDesc(reservationId).stream()
                .map(remboursementMapper::toResponse)
                .toList();
    }



    @Transactional
    public boolean rembourserAutomatiquement(UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
        if (reservation == null
                || reservation.getStatut() != StatutReservation.CONFIRMED
                || reservation.getEvent().getStatut() != StatusEvenment.CANCELLED) {
            return false;
        }

        Paiement paiement = paiementRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun paiement pour cette réservation"));
        List<Billet> billets = billetRepository.findByLigneReservationReservationId(reservationId);

        Remboursement remboursement = Remboursement.builder()
                .paiement(paiement)
                .motif("Annulation de l'événement par l'organisateur")
                .dateDemande(LocalDateTime.now())
                .montant(paiement.getMontant())
                .decision("Remboursement intégral automatique (annulation de l'événement)")
                .statut(StatutRemboursement.PENDING)
                .build();

        executerRemboursement(reservation, paiement, remboursement, billets);
        remboursementRepository.save(remboursement);
        return true;
    }


    // heapers

    private void checkOwner(Reservation reservation, UUID clientId) {
        if (!reservation.getClient().getId().equals(clientId)) {
            throw new ForbiddenOperationException("Cette réservation ne vous appartient pas");
        }
    }

    private void checkRemboursable(Reservation reservation) {
        if (reservation.getStatut() == StatutReservation.REFUNDED) {
            throw new BusinessRuleException("Cette réservation a déjà été remboursée");
        }
        if (reservation.getStatut() != StatutReservation.CONFIRMED) {
            throw new BusinessRuleException(
                    "Seule une réservation payée peut être remboursée (statut : " + reservation.getStatut() + ")");
        }
    }

    private String raisonRefus(Reservation reservation, List<Billet> billets, LocalDateTime now) {
        if (reservation.getEvent().getStatut() == StatusEvenment.CANCELLED) {
            return null;
        }
        LocalDateTime limite = reservation.getEvent().getDateDebut().minusHours(DELAI_REMBOURSEMENT_HEURES);
        if (now.isAfter(limite)) {
            return "Refusé : la demande doit être faite au moins " + DELAI_REMBOURSEMENT_HEURES
                    + "h avant le début de l'événement";
        }
        if (billets.stream().anyMatch(b -> b.getStatut() == StatutBillet.USED)) {
            return "Refusé : un billet de cette réservation a déjà été utilisé";
        }
        return null;
    }

    private void executerRemboursement(Reservation reservation, Paiement paiement,
                                       Remboursement remboursement, List<Billet> billets) {
        PaymentResult result = paymentGateway.refund(paiement.getMontant(), paiement.getReference());
        if (!result.success()) {
            throw new BusinessRuleException("Le remboursement a échoué, veuillez réessayer plus tard");
        }
        remboursement.approuver();
        reservation.setStatut(StatutReservation.REFUNDED);
        billets.forEach(billet -> billet.setStatut(StatutBillet.CANCELLED));
        if (reservation.getPromotion() != null) {
            promotionRepository.devrementUtilisation(reservation.getPromotion().getId());
        }
    }
}