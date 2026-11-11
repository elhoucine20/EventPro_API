package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.paiement.PaiementRequest;
import com.eventPro.eventPro.dto.paiement.PaiementResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.IdempotencyConflictException;
import com.eventPro.eventPro.exception.ReservationExpiredException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.paiement.PaiementMapper;
import com.eventPro.eventPro.model.Billet;
import com.eventPro.eventPro.model.CategorieBillet;
import com.eventPro.eventPro.model.Client;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.LigneReservation;
import com.eventPro.eventPro.model.Paiement;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.model.enums.StatutBillet;
import com.eventPro.eventPro.model.enums.StatutPaiement;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.paiement.PaymentGateway;
import com.eventPro.eventPro.paiement.PaymentResult;
import com.eventPro.eventPro.repository.BilletRepository;
import com.eventPro.eventPro.repository.PaiementRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaiementServiceTest {

    @Mock private PaiementRepository paiementRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private BilletRepository billetRepository;
    @Mock private PaiementMapper paiementMapper;
    @Mock private PaymentGateway paymentGateway;

    @InjectMocks private PaiementService service;

    @Captor private ArgumentCaptor<List<Billet>> billetsCaptor;
    @Captor private ArgumentCaptor<String> referenceCaptor;

    private static final String KEY = "key-1";

    private final UUID reservationId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final BigDecimal total = new BigDecimal("650.00");

    private Client client;
    private PaiementRequest request;          // requete valide par defaut : 650.00, pas d'echec
    private PaymentResult chargeReussie;
    private PaymentResult chargeEchouee;
    private PaiementResponse response;

    @BeforeEach
    void setUp() {
        client = mock(Client.class);
        lenient().when(client.getId()).thenReturn(clientId);

        request = requete("650.00", false);

        chargeReussie = mock(PaymentResult.class);
        lenient().when(chargeReussie.success()).thenReturn(true);
        chargeEchouee = mock(PaymentResult.class);
        lenient().when(chargeEchouee.success()).thenReturn(false);

        response = mock(PaiementResponse.class);
        lenient().when(paiementMapper.toResponse(any(Paiement.class))).thenReturn(response);
        lenient().when(paiementRepository.save(any(Paiement.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(paiementRepository.existsByReference(anyString())).thenReturn(false);
    }

    // ================= helpers : TOUJOURS les appeler dans une variable locale, jamais dans when(...) =================

    private PaiementRequest requete(String montant, Boolean simulerEchec) {
        PaiementRequest r = mock(PaiementRequest.class);
        lenient().when(r.idempotencyKey()).thenReturn(KEY);
        lenient().when(r.clientId()).thenReturn(clientId);
        lenient().when(r.montant()).thenReturn(new BigDecimal(montant));
        lenient().when(r.simulerEchec()).thenReturn(simulerEchec);
        return r;
    }

    private LigneReservation ligne(int quantite) {
        LigneReservation l = mock(LigneReservation.class);
        lenient().when(l.getQuantite()).thenReturn(quantite);
        lenient().when(l.getCategorieBillet()).thenReturn(mock(CategorieBillet.class));
        return l;
    }

    private Event event(StatusEvenment statut) {
        return Event.builder().statut(statut).build();
    }

    private Reservation reservation(StatutReservation statut, Event event, LocalDateTime expiration, List<LigneReservation> lignes) {
        Reservation r = mock(Reservation.class);
        lenient().when(r.getId()).thenReturn(reservationId);
        lenient().when(r.getStatut()).thenReturn(statut);
        lenient().when(r.getClient()).thenReturn(client);
        lenient().when(r.getEvent()).thenReturn(event);
        lenient().when(r.getDateExpiration()).thenReturn(expiration);
        lenient().when(r.getMontantTotal()).thenReturn(total);
        lenient().when(r.getLignes()).thenReturn(lignes);
        return r;
    }

    /** Reservation payable : PENDING_PAYMENT, event publie, expire dans 10 min, 2 lignes (2 + 1 billets). */
    private Reservation reservationPayable() {
        List<LigneReservation> lignes = List.of(ligne(2), ligne(1));
        Event event = event(StatusEvenment.PUBLISHED);
        return reservation(StatutReservation.PENDING_PAYMENT, event, LocalDateTime.now().plusMinutes(10), lignes);
    }

    private void stubReservation(Reservation reservation) {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
    }

    private Paiement paiementSauvegarde() {
        ArgumentCaptor<Paiement> captor = ArgumentCaptor.forClass(Paiement.class);
        verify(paiementRepository).save(captor.capture());
        return captor.getValue();
    }

    // =====================================================================
    @Nested
    @DisplayName("payer")
    class Payer {

        // ---------- reservation / idempotence ----------

        @Test
        void reservationIntrouvable_leveResourceNotFound() {
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.payer(reservationId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(reservationId.toString());

            verifyNoInteractions(paiementRepository, paymentGateway, billetRepository);
        }

        @Test
        void replayMemeReservationEtMemeMontant_retourneLaReponseSansRepayer() {
            Reservation reservation = reservationPayable();
            Paiement existant = mock(Paiement.class);
            when(existant.getReservation()).thenReturn(reservation);
            when(existant.getMontant()).thenReturn(new BigDecimal("650.0"));   // echelle differente : compareTo
            stubReservation(reservation);
            when(paiementRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existant));
            when(paiementMapper.toResponse(existant)).thenReturn(response);

            assertThat(service.payer(reservationId, request)).isSameAs(response);

            verifyNoInteractions(paymentGateway, billetRepository);
            verify(paiementRepository, never()).save(any());
        }

        @Test
        void replayAvecUneAutreReservation_leveIdempotencyConflict() {
            Reservation reservation = reservationPayable();
            Reservation autre = mock(Reservation.class);
            when(autre.getId()).thenReturn(UUID.randomUUID());
            Paiement existant = mock(Paiement.class);
            when(existant.getReservation()).thenReturn(autre);
            stubReservation(reservation);
            when(paiementRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existant));

            assertThatThrownBy(() -> service.payer(reservationId, request))
                    .isInstanceOf(IdempotencyConflictException.class);

            verifyNoInteractions(paymentGateway);
            verify(paiementRepository, never()).save(any());
        }

        @Test
        void replayAvecUnAutreMontant_leveIdempotencyConflict() {
            Reservation reservation = reservationPayable();
            Paiement existant = mock(Paiement.class);
            when(existant.getReservation()).thenReturn(reservation);
            when(existant.getMontant()).thenReturn(new BigDecimal("100.00"));
            stubReservation(reservation);
            when(paiementRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existant));

            assertThatThrownBy(() -> service.payer(reservationId, request))
                    .isInstanceOf(IdempotencyConflictException.class);

            verifyNoInteractions(paymentGateway);
        }

        // ---------- checks ----------

        @Test
        void pasLeProprietaire_leveForbidden() {
            Reservation reservation = reservationPayable();
            stubReservation(reservation);
            PaiementRequest autreClient = mock(PaiementRequest.class);
            when(autreClient.idempotencyKey()).thenReturn(KEY);
            when(autreClient.clientId()).thenReturn(UUID.randomUUID());

            assertThatThrownBy(() -> service.payer(reservationId, autreClient))
                    .isInstanceOf(ForbiddenOperationException.class);

            verifyNoInteractions(paymentGateway, billetRepository);
            verify(paiementRepository, never()).save(any());
        }

        @Test
        void reservationExpired_leveReservationExpired() {
            Reservation reservation = reservation(StatutReservation.EXPIRED, event(StatusEvenment.PUBLISHED),
                    LocalDateTime.now().minusMinutes(5), List.of());
            stubReservation(reservation);

            assertThatThrownBy(() -> service.payer(reservationId, request))
                    .isInstanceOf(ReservationExpiredException.class);

            verifyNoInteractions(paymentGateway);
        }

        @ParameterizedTest
        @EnumSource(value = StatutReservation.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING_PAYMENT", "EXPIRED"})
        void statutNonPayable_leveBusinessRule(StatutReservation statut) {
            Reservation reservation = reservation(statut, event(StatusEvenment.PUBLISHED),
                    LocalDateTime.now().plusMinutes(10), List.of());
            stubReservation(reservation);

            assertThatThrownBy(() -> service.payer(reservationId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining(statut.name());

            verifyNoInteractions(paymentGateway);
        }

        @Test
        void eventAnnule_leveBusinessRule() {
            Reservation reservation = reservation(StatutReservation.PENDING_PAYMENT, event(StatusEvenment.CANCELLED),
                    LocalDateTime.now().plusMinutes(10), List.of());
            stubReservation(reservation);

            assertThatThrownBy(() -> service.payer(reservationId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verifyNoInteractions(paymentGateway);
        }

        @Test
        void dateExpirationDepassee_leveReservationExpired() {
            Reservation reservation = reservation(StatutReservation.PENDING_PAYMENT, event(StatusEvenment.PUBLISHED),
                    LocalDateTime.now().minusMinutes(1), List.of());
            stubReservation(reservation);

            assertThatThrownBy(() -> service.payer(reservationId, request))
                    .isInstanceOf(ReservationExpiredException.class);

            verifyNoInteractions(paymentGateway);
        }

        @Test
        void mauvaisMontant_leveBusinessRule_sansCharger() {
            Reservation reservation = reservationPayable();
            stubReservation(reservation);
            PaiementRequest mauvais = requete("100.00", false);

            assertThatThrownBy(() -> service.payer(reservationId, mauvais))
                    .isInstanceOf(BusinessRuleException.class);

            verifyNoInteractions(paymentGateway, billetRepository);
            verify(paiementRepository, never()).save(any());
        }

        // ---------- succes ----------

        @Test
        void succes_confirmeLaReservation_etGenereLesBillets() {
            Reservation reservation = reservationPayable();
            stubReservation(reservation);
            when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

            PaiementResponse result = service.payer(reservationId, request);

            assertThat(result).isSameAs(response);
            Paiement p = paiementSauvegarde();
            assertThat(p.getStatut()).isEqualTo(StatutPaiement.SUCCESS);
            assertThat(p.getMontant()).isEqualByComparingTo("650.00");
            assertThat(p.getIdempotencyKey()).isEqualTo(KEY);
            assertThat(p.getReference()).startsWith("PAY-");
            assertThat(p.getDatePaiement()).isNotNull();
            assertThat(p.getReservation()).isSameAs(reservation);
            verify(reservation).setStatut(StatutReservation.CONFIRMED);
            verify(paymentGateway).charge(eq(total), referenceCaptor.capture(), eq(false));
            assertThat(referenceCaptor.getValue()).isEqualTo(p.getReference());
        }

        @Test
        void succes_genereUnBilletParPlace_toutesVALID_avecCodesUniques() {
            Reservation reservation = reservationPayable();   // lignes : 2 + 1
            stubReservation(reservation);
            when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

            service.payer(reservationId, request);

            verify(billetRepository).saveAll(billetsCaptor.capture());
            List<Billet> billets = billetsCaptor.getValue();
            assertThat(billets).hasSize(3);
            assertThat(billets).extracting(Billet::getStatut).containsOnly(StatutBillet.VALID);
            assertThat(billets).extracting(Billet::getCode).allMatch(c -> c.startsWith("BIL-")).doesNotHaveDuplicates();
            assertThat(billets).extracting(Billet::getDateEmission).doesNotContainNull();
        }

        @Test
        void montantEgalAvecUneAutreEchelle_estAccepte() {
            Reservation reservation = reservationPayable();
            stubReservation(reservation);
            PaiementRequest r = requete("650", false);   // 650 vs 650.00
            when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

            service.payer(reservationId, r);

            assertThat(paiementSauvegarde().getStatut()).isEqualTo(StatutPaiement.SUCCESS);
        }

        @Test
        void simulerEchecNull_estTraiteCommeFalse() {
            Reservation reservation = reservationPayable();
            stubReservation(reservation);
            PaiementRequest r = requete("650.00", null);
            when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

            service.payer(reservationId, r);

            verify(paymentGateway).charge(eq(total), anyString(), eq(false));
        }

        @Test
        void referenceDejaExistante_enRegenereUneAutre() {
            Reservation reservation = reservationPayable();
            stubReservation(reservation);
            when(paiementRepository.existsByReference(anyString())).thenReturn(true, false);
            when(paymentGateway.charge(eq(total), anyString(), anyBoolean())).thenReturn(chargeReussie);

            service.payer(reservationId, request);

            verify(paiementRepository, times(2)).existsByReference(anyString());
        }

        // ---------- echec ----------

        @Test
        void echecSimule_paiementFailed_reservationEtBilletsInchanges() {
            Reservation reservation = reservationPayable();
            stubReservation(reservation);
            PaiementRequest r = requete("650.00", true);
            when(paymentGateway.charge(eq(total), anyString(), eq(true))).thenReturn(chargeEchouee);

            PaiementResponse result = service.payer(reservationId, r);

            assertThat(result).isSameAs(response);
            assertThat(paiementSauvegarde().getStatut()).isEqualTo(StatutPaiement.FAILED);
            verify(reservation, never()).setStatut(any());
            verify(billetRepository, never()).saveAll(any());
        }

        @Test
        void reessaiApresUnEchec_reutiliseLePaiementExistant() {
            Reservation reservation = reservationPayable();
            Paiement echoue = Paiement.builder().reservation(reservation).statut(StatutPaiement.FAILED).build();
            stubReservation(reservation);
            when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.of(echoue));
            when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

            service.payer(reservationId, request);

            Paiement sauvegarde = paiementSauvegarde();
            assertThat(sauvegarde).isSameAs(echoue);
            assertThat(sauvegarde.getStatut()).isEqualTo(StatutPaiement.SUCCESS);
            verify(reservation).setStatut(StatutReservation.CONFIRMED);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("getByReservation")
    class GetByReservation {

        @Test
        void reservationIntrouvable_leveResourceNotFound() {
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByReservation(reservationId, clientId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void pasLeProprietaire_leveForbidden() {
            Reservation reservation = reservationPayable();
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> service.getByReservation(reservationId, UUID.randomUUID()))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(paiementRepository, never()).findByReservationId(any());
        }

        @Test
        void aucunPaiement_leveResourceNotFound() {
            Reservation reservation = reservationPayable();
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByReservation(reservationId, clientId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void nominal_retourneLePaiement() {
            Reservation reservation = reservationPayable();
            Paiement paiement = Paiement.builder().reservation(reservation).build();
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.of(paiement));
            when(paiementMapper.toResponse(paiement)).thenReturn(response);

            assertThat(service.getByReservation(reservationId, clientId)).isSameAs(response);
        }
    }
}