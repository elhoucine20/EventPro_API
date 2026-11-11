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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock private BilletRepository billetRepository;
    @Mock private AgentControleRepository agentControleRepository;
    @Mock private TicketMapper ticketMapper;

    @InjectMocks private CheckInService service;

    private static final String CODE = "BIL-ABC123";

    private final UUID agentId = UUID.randomUUID();
    private final LocalDateTime dateControle = LocalDateTime.of(2026, 12, 20, 19, 30);

    private TicketResponse ticket;

    @BeforeEach
    void setUp() {
        ticket = mock(TicketResponse.class);
        lenient().when(ticketMapper.toResponse(any(Billet.class))).thenReturn(ticket);
    }

    /** Toujours dans une variable locale, jamais dans when(...). */
    private Billet billet(StatutBillet statut, StatusEvenment statutEvent) {
        Event event = Event.builder().statut(statutEvent).build();
        Reservation reservation = mock(Reservation.class);
        lenient().when(reservation.getEvent()).thenReturn(event);
        LigneReservation ligne = mock(LigneReservation.class);
        lenient().when(ligne.getReservation()).thenReturn(reservation);
        Billet b = mock(Billet.class);
        lenient().when(b.getLigneReservation()).thenReturn(ligne);
        lenient().when(b.getStatut()).thenReturn(statut);
        lenient().when(b.getDateControle()).thenReturn(dateControle);
        return b;
    }

    // =====================================================================
    @Nested
    @DisplayName("verifier")
    class Verifier {

        @Test
        void agentIntrouvable_leveResourceNotFound_sansChercherLeBillet() {
            when(agentControleRepository.existsById(agentId)).thenReturn(false);

            assertThatThrownBy(() -> service.verifier(CODE, agentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(agentId.toString());

            verifyNoInteractions(billetRepository);
        }

        @Test
        void codeNormalise_espacesEtMinuscules() {
            Billet billet = billet(StatutBillet.VALID, StatusEvenment.PUBLISHED);
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode("BIL-ABC123")).thenReturn(Optional.of(billet));

            VerificationResponse result = service.verifier("  bil-abc123 ", agentId);

            assertThat(result.accesAutorise()).isTrue();
            verify(billetRepository).findByCode("BIL-ABC123");
        }

        @Test
        void billetIntrouvable_leveResourceNotFound() {
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifier(CODE, agentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(CODE);
        }

        @Test
        void billetValide_accesAutorise_etNeComposteRien() {
            Billet billet = billet(StatutBillet.VALID, StatusEvenment.PUBLISHED);
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

            VerificationResponse result = service.verifier(CODE, agentId);

            assertThat(result.accesAutorise()).isTrue();
            assertThat(result.message()).isEqualTo("Billet valide");
            assertThat(result.billet()).isSameAs(ticket);
            verify(billetRepository, never()).composter(any(), any());
        }

        @Test
        void billetDejaUtilise_accesRefuse() {
            Billet billet = billet(StatutBillet.USED, StatusEvenment.PUBLISHED);
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

            VerificationResponse result = service.verifier(CODE, agentId);

            assertThat(result.accesAutorise()).isFalse();
            assertThat(result.message()).contains("déja utilisé");
        }

        @Test
        void billetAnnule_accesRefuse() {
            Billet billet = billet(StatutBillet.CANCELLED, StatusEvenment.PUBLISHED);
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

            VerificationResponse result = service.verifier(CODE, agentId);

            assertThat(result.accesAutorise()).isFalse();
            assertThat(result.message()).contains("annulé");
        }

        @Test
        void evenementAnnule_accesRefuse_memePourUnBilletValide() {
            Billet billet = billet(StatutBillet.VALID, StatusEvenment.CANCELLED);
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

            VerificationResponse result = service.verifier(CODE, agentId);

            assertThat(result.accesAutorise()).isFalse();
            assertThat(result.message()).contains("événement a été annulé");
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("composter")
    class Composter {

        private CheckInRequest request() {
            CheckInRequest r = mock(CheckInRequest.class);
            lenient().when(r.agentId()).thenReturn(agentId);
            lenient().when(r.code()).thenReturn(CODE);
            return r;
        }

        @Test
        void agentIntrouvable_leveResourceNotFound() {
            CheckInRequest request = request();
            when(agentControleRepository.existsById(agentId)).thenReturn(false);

            assertThatThrownBy(() -> service.composter(request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(billetRepository);
        }

        @Test
        void billetIntrouvable_leveResourceNotFound_sansComposter() {
            CheckInRequest request = request();
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.composter(request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(billetRepository, never()).composter(any(), any());
        }

        @Test
        void evenementAnnule_leveTicketInvalid_sansComposter() {
            CheckInRequest request = request();
            Billet billet = billet(StatutBillet.VALID, StatusEvenment.CANCELLED);
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));

            assertThatThrownBy(() -> service.composter(request))
                    .isInstanceOf(TicketInvalidException.class)
                    .hasMessageContaining("annulé");

            verify(billetRepository, never()).composter(any(), any());
        }

        @Test
        void nominal_composteLeBillet_etAutoriseLAcces() {
            CheckInRequest request = request();
            Billet billet = billet(StatutBillet.USED, StatusEvenment.PUBLISHED);   // etat reel apres l'UPDATE
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));
            when(billetRepository.composter(eq(CODE), any(LocalDateTime.class))).thenReturn(1);

            VerificationResponse result = service.composter(request);

            assertThat(result.accesAutorise()).isTrue();
            assertThat(result.message()).contains("composté");
            assertThat(result.billet()).isSameAs(ticket);
            verify(billetRepository).composter(eq(CODE), any(LocalDateTime.class));
        }

        @Test
        void billetDejaUtilise_leveTicketAlreadyUsed() {
            CheckInRequest request = request();
            Billet billet = billet(StatutBillet.USED, StatusEvenment.PUBLISHED);
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));
            when(billetRepository.composter(eq(CODE), any(LocalDateTime.class))).thenReturn(0);

            assertThatThrownBy(() -> service.composter(request))
                    .isInstanceOf(TicketAlreadyUsedException.class)
                    .hasMessageContaining("déja utilisé");
        }

        @Test
        void billetAnnule_leveTicketInvalid() {
            CheckInRequest request = request();
            Billet billet = billet(StatutBillet.CANCELLED, StatusEvenment.PUBLISHED);
            when(agentControleRepository.existsById(agentId)).thenReturn(true);
            when(billetRepository.findByCode(CODE)).thenReturn(Optional.of(billet));
            when(billetRepository.composter(eq(CODE), any(LocalDateTime.class))).thenReturn(0);

            assertThatThrownBy(() -> service.composter(request))
                    .isInstanceOf(TicketInvalidException.class)
                    .hasMessageContaining("annulé");
        }
    }
}