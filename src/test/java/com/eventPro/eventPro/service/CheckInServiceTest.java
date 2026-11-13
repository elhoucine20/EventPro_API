package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.ticket.CheckInRequest;
import com.eventPro.eventPro.dto.ticket.TicketResponse;
import com.eventPro.eventPro.dto.ticket.VerificationResponse;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.exception.TicketAlreadyUsedException;
import com.eventPro.eventPro.exception.TicketInvalidException;
import com.eventPro.eventPro.mapper.ticket.TicketMapper;
import com.eventPro.eventPro.model.Billet;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.LigneReservation;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.model.enums.StatutBillet;
import com.eventPro.eventPro.repository.AgentControleRepository;
import com.eventPro.eventPro.repository.BilletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT) // pas d'erreur si un stub n'est pas utilise dans un test
class CheckInServiceTest {

    @Mock
    private BilletRepository billetRepository;
    @Mock
    private AgentControleRepository agentControleRepository;
    @Mock
    private TicketMapper ticketMapper;

    @InjectMocks
    private CheckInService service;

    private static final String CODE = "BIL-ABC123";

    private final UUID agentId = UUID.randomUUID();
    private final LocalDateTime dateControle = LocalDateTime.of(2026, 12, 20, 19, 30);

    private TicketResponse ticket;

    @BeforeEach
    void setUp() {
        // le mapper renvoie toujours le meme "ticket" pour n'importe quel billet
        ticket = mock(TicketResponse.class);
        when(ticketMapper.toResponse(any(Billet.class))).thenReturn(ticket);
    }

    // ---------- petits helpers (comme OneUser() avant) ----------
    // billet avec un statut, rattache a un event qui a le statut voulu
    private Billet OneBillet(StatutBillet statut, StatusEvenment statutEvent) {
        Event event = Event.builder().statut(statutEvent).build();
        Reservation reservation = mock(Reservation.class);
        when(reservation.getEvent()).thenReturn(event);
        LigneReservation ligne = mock(LigneReservation.class);
        when(ligne.getReservation()).thenReturn(reservation);
        Billet b = mock(Billet.class);
        when(b.getLigneReservation()).thenReturn(ligne);
        when(b.getStatut()).thenReturn(statut);
        when(b.getDateControle()).thenReturn(dateControle);
        return b;
    }

    private CheckInRequest OneRequest() {
        CheckInRequest r = mock(CheckInRequest.class);
        when(r.agentId()).thenReturn(agentId);
        when(r.code()).thenReturn(CODE);
        return r;
    }

    // =====================================================================
    // verifier
    // =====================================================================
    @Test
    void verifier_agentIntrouvable_ResourceNotFound_sansChercherLeBillet() {
        when(agentControleRepository.existsById(agentId)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.verifier(CODE, agentId));

        assertTrue(ex.getMessage().contains(agentId.toString()));
        verifyNoInteractions(billetRepository);
    }

    @Test
    void verifier_codeNormalise_espacesEtMinuscules() {
        // ARRANGE
        Billet billet = OneBillet(StatutBillet.VALID, StatusEvenment.PUBLISHED);
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode("BIL-ABC123")).thenReturn(Optional.of(billet));

        // ACT : le scanner envoie "  bil-abc123 "
        VerificationResponse result = service.verifier("  bil-abc123 ", agentId);

        // ASSERT
        assertTrue(result.accesAutorise());
        verify(billetRepository).findByCode("BIL-ABC123");
    }

    @Test
    void verifier_billetIntrouvable_ResourceNotFound() {
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.verifier(CODE, agentId));

        assertTrue(ex.getMessage().contains(CODE));
    }

    @Test
    void verifier_billetValide_accesAutorise_etNeComposteRien() {
        // ARRANGE
        Billet billet = OneBillet(StatutBillet.VALID, StatusEvenment.PUBLISHED);
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

        // ACT
        VerificationResponse result = service.verifier(CODE, agentId);

        // ASSERT
        assertTrue(result.accesAutorise());
        assertEquals("Billet valide", result.message());
        assertSame(ticket, result.billet());
        verify(billetRepository, never()).composter(any(), any()); // verifier ne modifie rien
    }

    @Test
    void verifier_billetDejaUtilise_accesRefuse() {
        Billet billet = OneBillet(StatutBillet.USED, StatusEvenment.PUBLISHED);
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

        VerificationResponse result = service.verifier(CODE, agentId);

        assertFalse(result.accesAutorise());
        assertTrue(result.message().contains("déja utilisé"));
    }

    @Test
    void verifier_billetAnnule_accesRefuse() {
        Billet billet = OneBillet(StatutBillet.CANCELLED, StatusEvenment.PUBLISHED);
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

        VerificationResponse result = service.verifier(CODE, agentId);

        assertFalse(result.accesAutorise());
        assertTrue(result.message().contains("annulé"));
    }

    @Test
    void verifier_evenementAnnule_accesRefuse_memePourUnBilletValide() {
        Billet billet = OneBillet(StatutBillet.VALID, StatusEvenment.CANCELLED);
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

        VerificationResponse result = service.verifier(CODE, agentId);

        assertFalse(result.accesAutorise());
        assertTrue(result.message().contains("événement a été annulé"));
    }

    // =====================================================================
    // composter
    // =====================================================================
    @Test
    void composter_agentIntrouvable_ResourceNotFound() {
        CheckInRequest request = OneRequest();
        when(agentControleRepository.existsById(agentId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.composter(request));

        verifyNoInteractions(billetRepository);
    }

    @Test
    void composter_billetIntrouvable_ResourceNotFound_sansComposter() {
        CheckInRequest request = OneRequest();
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.composter(request));

        verify(billetRepository, never()).composter(any(), any());
    }

    @Test
    void composter_evenementAnnule_TicketInvalid_sansComposter() {
        CheckInRequest request = OneRequest();
        Billet billet = OneBillet(StatutBillet.VALID, StatusEvenment.CANCELLED);
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

        TicketInvalidException ex = assertThrows(TicketInvalidException.class, () -> service.composter(request));

        assertTrue(ex.getMessage().contains("annulé"));
        verify(billetRepository, never()).composter(any(), any()); // refuse avant l'UPDATE
    }

    @Test
    void composter_nominal_composteLeBillet_etAutoriseLAcces() {
        // ARRANGE : l'UPDATE retourne 1 ligne ; le billet relu est USED
        CheckInRequest request = OneRequest();
        Billet billet = OneBillet(StatutBillet.USED, StatusEvenment.PUBLISHED);
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));
        when(billetRepository.composter(eq(CODE), any(LocalDateTime.class))).thenReturn(1);

        // ACT
        VerificationResponse result = service.composter(request);

        // ASSERT
        assertTrue(result.accesAutorise());
        assertTrue(result.message().contains("composté"));
        assertSame(ticket, result.billet());
        verify(billetRepository).composter(eq(CODE), any(LocalDateTime.class));
    }

    @Test
    void composter_billetDejaUtilise_TicketAlreadyUsed() {
        // l'UPDATE retourne 0 et le billet est deja USED
        CheckInRequest request = OneRequest();
        Billet billet = OneBillet(StatutBillet.USED, StatusEvenment.PUBLISHED);
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));
        when(billetRepository.composter(eq(CODE), any(LocalDateTime.class))).thenReturn(0);

        TicketAlreadyUsedException ex = assertThrows(TicketAlreadyUsedException.class, () -> service.composter(request));

        assertTrue(ex.getMessage().contains("déja utilisé"));
    }

    @Test
    void composter_billetAnnule_TicketInvalid() {
        // l'UPDATE retourne 0 et le billet est CANCELLED
        CheckInRequest request = OneRequest();
        Billet billet = OneBillet(StatutBillet.CANCELLED, StatusEvenment.PUBLISHED);
        when(agentControleRepository.existsById(agentId)).thenReturn(true);
        when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));
        when(billetRepository.composter(eq(CODE), any(LocalDateTime.class))).thenReturn(0);

        TicketInvalidException ex = assertThrows(TicketInvalidException.class, () -> service.composter(request));

        assertTrue(ex.getMessage().contains("annulé"));
    }
}