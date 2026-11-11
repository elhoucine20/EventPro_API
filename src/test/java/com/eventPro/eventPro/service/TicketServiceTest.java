package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.ticket.TicketResponse;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.ticket.TicketMapper;
import com.eventPro.eventPro.model.Billet;
import com.eventPro.eventPro.model.Client;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.repository.BilletRepository;
import com.eventPro.eventPro.repository.ClientRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private BilletRepository billetRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private TicketMapper ticketMapper;

    @InjectMocks private TicketService service;

    private final UUID reservationId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    private Reservation reservationDe(UUID proprietaire) {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(proprietaire);
        Reservation reservation = mock(Reservation.class);
        when(reservation.getClient()).thenReturn(client);
        return reservation;
    }

    // =====================================================================
    @Nested
    @DisplayName("listByReservation")
    class ListByReservation {

        @Test
        void reservationIntrouvable_leveResourceNotFound() {
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listByReservation(reservationId, clientId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(reservationId.toString());

            verify(billetRepository, never()).findByLigneReservationReservationId(any());
        }

        @Test
        void pasLeProprietaire_leveForbidden() {
            Reservation reservation = reservationDe(UUID.randomUUID());
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> service.listByReservation(reservationId, clientId))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(billetRepository, never()).findByLigneReservationReservationId(any());
        }

        @Test
        void nominal_retourneLesBilletsMappes() {
            Reservation reservation = reservationDe(clientId);
            Billet b1 = mock(Billet.class);
            Billet b2 = mock(Billet.class);
            TicketResponse t1 = mock(TicketResponse.class);
            TicketResponse t2 = mock(TicketResponse.class);
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(List.of(b1, b2));
            when(ticketMapper.toResponse(b1)).thenReturn(t1);
            when(ticketMapper.toResponse(b2)).thenReturn(t2);

            assertThat(service.listByReservation(reservationId, clientId)).containsExactly(t1, t2);
        }

        @Test
        void aucunBillet_retourneListeVide() {
            Reservation reservation = reservationDe(clientId);
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(List.of());

            assertThat(service.listByReservation(reservationId, clientId)).isEmpty();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listByClient")
    class ListByClient {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        void clientIntrouvable_leveResourceNotFound() {
            when(clientRepository.existsById(clientId)).thenReturn(false);

            assertThatThrownBy(() -> service.listByClient(clientId, pageable))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(clientId.toString());

            verify(billetRepository, never()).findByLigneReservationReservationClientId(any(), any());
        }

        @Test
        void nominal_retourneLaPageMappee() {
            Billet b1 = mock(Billet.class);
            TicketResponse t1 = mock(TicketResponse.class);
            when(clientRepository.existsById(clientId)).thenReturn(true);
            when(billetRepository.findByLigneReservationReservationClientId(clientId, pageable))
                    .thenReturn(new PageImpl<>(List.of(b1), pageable, 1));
            when(ticketMapper.toResponse(b1)).thenReturn(t1);

            Page<TicketResponse> page = service.listByClient(clientId, pageable);

            assertThat(page.getContent()).containsExactly(t1);
            assertThat(page.getTotalElements()).isEqualTo(1);
        }
    }
}