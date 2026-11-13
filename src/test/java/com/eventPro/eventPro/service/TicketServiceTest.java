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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceTest {

    @Mock
    private BilletRepository billetRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private TicketMapper ticketMapper;

    @InjectMocks
    private TicketService service;

    private final UUID reservationId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final Pageable pageable = PageRequest.of(0, 10);

    // ---------- petit helper (comme OneUser() avant) ----------
    // reservation qui appartient au client "proprietaire"
    private Reservation OneReservation(UUID proprietaire) {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(proprietaire);
        Reservation reservation = mock(Reservation.class);
        when(reservation.getClient()).thenReturn(client);
        return reservation;
    }

    // =====================================================================
    // listByReservation
    // =====================================================================
    @Test
    void listByReservation_reservationIntrouvable_ResourceNotFound() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.listByReservation(reservationId, clientId));

        assertTrue(ex.getMessage().contains(reservationId.toString()));
        verify(billetRepository, never()).findByLigneReservationReservationId(any());
    }

    @Test
    void listByReservation_pasLeProprietaire_Forbidden() {
        Reservation reservation = OneReservation(UUID.randomUUID()); // appartient a un autre client
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        assertThrows(ForbiddenOperationException.class, () -> service.listByReservation(reservationId, clientId));

        verify(billetRepository, never()).findByLigneReservationReservationId(any());
    }

    @Test
    void listByReservation_nominal_retourneLesBilletsMappes() {
        // ARRANGE
        Reservation reservation = OneReservation(clientId);
        Billet b1 = mock(Billet.class);
        Billet b2 = mock(Billet.class);
        TicketResponse t1 = mock(TicketResponse.class);
        TicketResponse t2 = mock(TicketResponse.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(List.of(b1, b2));
        when(ticketMapper.toResponse(b1)).thenReturn(t1);
        when(ticketMapper.toResponse(b2)).thenReturn(t2);

        // ACT
        List<TicketResponse> result = service.listByReservation(reservationId, clientId);

        // ASSERT
        assertEquals(2, result.size());
        assertSame(t1, result.get(0));
        assertSame(t2, result.get(1));
    }

    @Test
    void listByReservation_aucunBillet_retourneListeVide() {
        Reservation reservation = OneReservation(clientId);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(List.of());

        assertTrue(service.listByReservation(reservationId, clientId).isEmpty());
    }

    // =====================================================================
    // listByClient
    // =====================================================================
    @Test
    void listByClient_clientIntrouvable_ResourceNotFound() {
        when(clientRepository.existsById(clientId)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.listByClient(clientId, pageable));

        assertTrue(ex.getMessage().contains(clientId.toString()));
        verify(billetRepository, never()).findByLigneReservationReservationClientId(any(), any());
    }

    @Test
    void listByClient_nominal_retourneLaPageMappee() {
        // ARRANGE
        Billet b1 = mock(Billet.class);
        TicketResponse t1 = mock(TicketResponse.class);
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(billetRepository.findByLigneReservationReservationClientId(clientId, pageable))
                .thenReturn(new PageImpl<>(List.of(b1), pageable, 1));
        when(ticketMapper.toResponse(b1)).thenReturn(t1);

        // ACT
        Page<TicketResponse> page = service.listByClient(clientId, pageable);

        // ASSERT
        assertEquals(1, page.getContent().size());
        assertSame(t1, page.getContent().get(0));
        assertEquals(1, page.getTotalElements());
    }
}