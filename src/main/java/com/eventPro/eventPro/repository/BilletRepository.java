package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Billet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public interface BilletRepository extends JpaRepository<Billet, UUID> {

    List<Billet> findByLigneReservationReservationId(UUID reservationId);// romborcementy
}
