package com.eventPro.eventPro.scheduler;

import com.eventPro.eventPro.repository.ReservationRepository;
import com.eventPro.eventPro.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j // "Simple Loggin Facade For Java"
@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    // c'est une api
    // au lieu de declarer  cette varaible nous peux utiliser l'annotation de lombol => @slf4j
  //  private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private static final int BATCH_SIZE = 100; // size de les reservations doit select dans la base de données (pagination maximum 100)

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${app.reservation.expiration-check-ms:60000}")  // ???? chaque mot ???
    public void expireReservations() {
        List<UUID> ids = reservationRepository.findExpiredIds(LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));  // 0=>premier page

        int expired = 0; // count des reseravtions expired
        for (UUID id : ids) {  // traitment de experation pour chaque reservation
            try {
                if (reservationService.expirer(id)) { // ???
                    expired++;
                }
            } catch (Exception e) {
                log.error("Echec de l'expiration de la reservation {}", id, e); // {}=> espace reserver a la parametre 'id' et 'e' c'est une
            }
        }
        if (expired > 0) {
            log.info("{} reservation(s) expiree(s)", expired); // {}=> est une espace resever a la parametre 'expired'
        }
    }
}


