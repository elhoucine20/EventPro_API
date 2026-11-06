package com.eventPro.eventPro.scheduler;

import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.repository.ReservationRepository;
import com.eventPro.eventPro.service.RemboursementService;
import com.eventPro.eventPro.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventCancellationScheduler {

    private static final int BATCH_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final RemboursementService remboursementService;
    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${app.event.cancellation-check-ms:60000}")
    public void traiterEvenementsAnnules() {
        int rembourses = traiterParLots(StatutReservation.CONFIRMED,
                remboursementService::rembourserAutomatiquement);
        int annulees = traiterParLots(StatutReservation.PENDING_PAYMENT,
                reservationService::annulerPourEvenementAnnule);

        if (rembourses > 0 || annulees > 0) {
            log.info("evenements annules : {} reservation(s) remboursee(s), {} reservation(s) annulee(s)",
                    rembourses, annulees);
        }
    }

    private int traiterParLots(StatutReservation statut, Predicate<UUID> action) {
        int total = 0;
        int traite;
        do {
            traite = 0;
            List<UUID> ids = reservationRepository
                    .findIdsByEventAnnuleEtStatut(statut, PageRequest.of(0, BATCH_SIZE));
            for (UUID id : ids) {
                try {
                    if (action.test(id)) {
                        traite++;
                    }
                } catch (Exception e) {
                    log.error("echec du traitement de la reservation {} ({})", id, statut, e);
                }
            }
            total += traite;
        } while (traite > 0);
        return total;
    }
}