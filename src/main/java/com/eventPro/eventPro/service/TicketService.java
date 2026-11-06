package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.ticket.TicketResponse;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.ticket.TicketMapper;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.repository.BilletRepository;
import com.eventPro.eventPro.repository.ClientRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final BilletRepository billetRepository;
    private final ReservationRepository reservationRepository;
    private final ClientRepository clientRepository;
    private final TicketMapper ticketMapper;

    @Transactional(readOnly = true)
    public List<TicketResponse> listByReservation(UUID reservationId, UUID clientId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation introuvable : " + reservationId));
        if (!reservation.getClient().getId().equals(clientId)) {
            throw new ForbiddenOperationException("Cette réservation ne vous appartient pas");
        }
        return billetRepository.findByLigneReservationReservationId(reservationId).stream()
                .map(ticketMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> listByClient(UUID clientId, Pageable pageable) {
        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Client introuvable : " + clientId);
        }
        return billetRepository.findByLigneReservationReservationClientId(clientId, pageable)
                .map(ticketMapper::toResponse);
    }
}