package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.paiement.PaiementRequest;
import com.eventPro.eventPro.dto.paiement.PaiementResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.paiement.PaiementMapper;
import com.eventPro.eventPro.model.Billet;
import com.eventPro.eventPro.model.LigneReservation;
import com.eventPro.eventPro.model.Paiement;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.enums.StatutBillet;
import com.eventPro.eventPro.model.enums.StatutPaiement;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.paiement.PaymentGateway;
import com.eventPro.eventPro.paiement.PaymentResult;
import com.eventPro.eventPro.repository.BilletRepository;
import com.eventPro.eventPro.repository.PaiementRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaiementService {

    private final PaiementRepository paiementRepository;
    private final ReservationRepository reservationRepository;
    private final BilletRepository billetRepository;
    private final PaiementMapper paiementMapper;
    private final PaymentGateway paymentGateway;


    @Transactional
    public PaiementResponse payer(UUID reservationId, PaiementRequest request){
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation introuvable : " + reservationId));

        checkOwner(reservation, request.clientId());
        checkPayable(reservation, now);

        Paiement paiement = paiementRepository.findByReservationId(reservationId)
                .orElseGet(() -> Paiement.builder().reservation(reservation).build()); // orElseGet ??
        paiement.setReference(generateReference());
        paiement.setMontant(reservation.getMontantTotal());
        paiement.setDatePaiement(now);

        boolean simulerEchec = Boolean.TRUE.equals(request.simulerEchec());
        PaymentResult result = paymentGateway.charge(paiement.getMontant(), paiement.getReference(), simulerEchec);

        if (result.success()) {
            paiement.setStatut(StatutPaiement.SUCCESS);
            reservation.setStatut(StatutReservation.CONFIRMED);
            genererBillets(reservation, now);
        } else {
            paiement.setStatut(StatutPaiement.FAILED);
        }

        return paiementMapper.toResponse(paiementRepository.save(paiement));
    }

    //
    @Transactional(readOnly = true)
    public PaiementResponse getByReservation(UUID reservationId, UUID clientId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation introuvable : " + reservationId));
        checkOwner(reservation, clientId);
        Paiement paiement = paiementRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun paiement pour cette reservation"));
        return paiementMapper.toResponse(paiement);
    }



    // heapers
    private void checkOwner(Reservation reservation, UUID clientId) {
        if (!reservation.getClient().getId().equals(clientId)) {
            throw new ForbiddenOperationException("Cette reservation ne vous appartient pas");
        }
    }

    private void checkPayable(Reservation reservation, LocalDateTime now) {
        if (reservation.getStatut() != StatutReservation.PENDING_PAYMENT) {
            throw new BusinessRuleException(
                    "Cette reservation ne peut pas etre payee (statut : " + reservation.getStatut() + ")");
        }
        if (reservation.getDateExpiration().isBefore(now)) {
            throw new BusinessRuleException("La reservation a expire");
        }
    }

    private String generateReference() {
        String reference;
        do {
            reference = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        } while (paiementRepository.existsByReference((reference)));
        return reference;
    }

    private void genererBillets(Reservation reservation, LocalDateTime now) {
        List<Billet> billets = new ArrayList<>();
        for (LigneReservation ligne : reservation.getLignes()) {
            for (int i = 0; i < ligne.getQuantite(); i++) {
                billets.add(Billet.builder()
                        .code(generateBilletCode())
                        .dateEmission(now)
                        .statut(StatutBillet.VALID)
                        .ligneReservation(ligne)
                        .build());
            }
        }
        billetRepository.saveAll(billets);
    }

    private String generateBilletCode() {
        return "BIL-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

}
