package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.remboursement.RemboursementRequest;
import com.eventPro.eventPro.dto.remboursement.RemboursementResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.remboursement.RemboursementMapper;
import com.eventPro.eventPro.model.Billet;
import com.eventPro.eventPro.model.Client;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.Paiement;
import com.eventPro.eventPro.model.Promotion;
import com.eventPro.eventPro.model.Remboursement;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.model.enums.StatutBillet;
import com.eventPro.eventPro.model.enums.StatutRemboursement;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.paiement.PaymentGateway;
import com.eventPro.eventPro.paiement.PaymentResult;
import com.eventPro.eventPro.repository.BilletRepository;
import com.eventPro.eventPro.repository.PaiementRepository;
import com.eventPro.eventPro.repository.PromotionRepository;
import com.eventPro.eventPro.repository.RemboursementRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemboursementServiceTest {

    @Mock private RemboursementRepository remboursementRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PaiementRepository paiementRepository;
    @Mock private BilletRepository billetRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private RemboursementMapper remboursementMapper;

    @InjectMocks private RemboursementService service;

    private static final String PAY_REF = "PAY-24B841D23C";

    private final UUID reservationId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID paiementId = UUID.randomUUID();
    private final UUID promoId = UUID.randomUUID();
    private final UUID remboursementId = UUID.randomUUID();
    private final BigDecimal montant = new BigDecimal("650.00");

    private Client client;
    private RemboursementRequest request;
    private PaymentResult paiementReussi;
    private PaymentResult paiementEchoue;
    private RemboursementResponse response;

    @BeforeEach
    void setUp() {
        client = mock(Client.class);
        lenient().when(client.getId()).thenReturn(clientId);

        request = mock(RemboursementRequest.class);
        lenient().when(request.clientId()).thenReturn(clientId);
        lenient().when(request.motif()).thenReturn("Empechement de derniere minute");

        paiementReussi = mock(PaymentResult.class);
        lenient().when(paiementReussi.success()).thenReturn(true);
        paiementEchoue = mock(PaymentResult.class);
        lenient().when(paiementEchoue.success()).thenReturn(false);

        response = mock(RemboursementResponse.class);
        lenient().when(remboursementMapper.toResponse(any(Remboursement.class))).thenReturn(response);
        lenient().when(remboursementRepository.save(any(Remboursement.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ================= helpers : TOUJOURS les appeler dans une variable locale, jamais dans when(...) =================

    /** Event dont le debut est dans `heures` heures. */
    private Event eventDans(long heures, StatusEvenment statut) {
        return Event.builder().statut(statut).dateDebut(LocalDateTime.now().plusHours(heures)).build();
    }

    private Reservation reservation(StatutReservation statut, Event event, Promotion promotion) {
        Reservation r = mock(Reservation.class);
        lenient().when(r.getId()).thenReturn(reservationId);
        lenient().when(r.getStatut()).thenReturn(statut);
        lenient().when(r.getClient()).thenReturn(client);
        lenient().when(r.getEvent()).thenReturn(event);
        lenient().when(r.getPromotion()).thenReturn(promotion);
        return r;
    }

    private Paiement paiement(Reservation reservation) {
        Paiement p = mock(Paiement.class);
        lenient().when(p.getId()).thenReturn(paiementId);
        lenient().when(p.getMontant()).thenReturn(montant);
        lenient().when(p.getReference()).thenReturn(PAY_REF);
        lenient().when(p.getReservation()).thenReturn(reservation);
        return p;
    }

    private Billet billet(StatutBillet statut) {
        Billet b = mock(Billet.class);
        lenient().when(b.getStatut()).thenReturn(statut);
        return b;
    }

    private Promotion promotion() {
        return Promotion.builder().id(promoId).build();
    }

    /** Branche reservation / paiement / billets pour `demander`. */
    private void stubDemande(Reservation reservation, Paiement paiement, List<Billet> billets) {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.of(paiement));
        when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(billets);
    }

    private Remboursement remboursementSauvegarde() {
        ArgumentCaptor<Remboursement> captor = ArgumentCaptor.forClass(Remboursement.class);
        verify(remboursementRepository).save(captor.capture());
        return captor.getValue();
    }

    // =====================================================================
    @Nested
    @DisplayName("demander")
    class Demander {

        @Test
        void reservationIntrouvable_leveResourceNotFound() {
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.demander(reservationId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(reservationId.toString());

            verifyNoInteractions(paiementRepository, paymentGateway);
        }

        @Test
        void pasLeProprietaire_leveForbidden() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(100, StatusEvenment.PUBLISHED), null);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
            RemboursementRequest autre = mock(RemboursementRequest.class);
            when(autre.clientId()).thenReturn(UUID.randomUUID());

            assertThatThrownBy(() -> service.demander(reservationId, autre))
                    .isInstanceOf(ForbiddenOperationException.class);

            verifyNoInteractions(paymentGateway);
            verify(remboursementRepository, never()).save(any());
        }

        @Test
        void dejaRemboursee_leveBusinessRule() {
            Reservation reservation = reservation(StatutReservation.REFUNDED, eventDans(100, StatusEvenment.PUBLISHED), null);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> service.demander(reservationId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("déjà été remboursée");

            verifyNoInteractions(paymentGateway);
        }

        @ParameterizedTest
        @EnumSource(value = StatutReservation.class, mode = EnumSource.Mode.EXCLUDE, names = {"CONFIRMED", "REFUNDED"})
        void reservationNonPayee_leveBusinessRule(StatutReservation statut) {
            Reservation reservation = reservation(statut, eventDans(100, StatusEvenment.PUBLISHED), null);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> service.demander(reservationId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining(statut.name());

            verify(remboursementRepository, never()).save(any());
            verifyNoInteractions(paymentGateway);
        }

        @Test
        void paiementIntrouvable_leveResourceNotFound() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(100, StatusEvenment.PUBLISHED), null);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
            when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.demander(reservationId, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(paymentGateway);
        }

        @Test
        void refusDejaEnregistre_retourneLaMemeReponse_sansRienRefaire() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(10, StatusEvenment.PUBLISHED), null);
            Paiement paiement = paiement(reservation);
            Remboursement dejaRefuse = Remboursement.builder().statut(StatutRemboursement.REJECTED).build();
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
            when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.of(paiement));
            when(remboursementRepository.findFirstByPaiementIdAndStatut(paiementId, StatutRemboursement.REJECTED))
                    .thenReturn(Optional.of(dejaRefuse));
            RemboursementResponse existante = mock(RemboursementResponse.class);
            when(remboursementMapper.toResponse(dejaRefuse)).thenReturn(existante);

            assertThat(service.demander(reservationId, request)).isSameAs(existante);

            verify(remboursementRepository, never()).save(any());
            verifyNoInteractions(paymentGateway, billetRepository);
        }

        @Test
        void nominalPlusDe48h_rembourseIntegralement() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(30 * 24, StatusEvenment.PUBLISHED), null);
            Paiement paiement = paiement(reservation);
            Billet b1 = billet(StatutBillet.VALID);
            Billet b2 = billet(StatutBillet.VALID);
            stubDemande(reservation, paiement, List.of(b1, b2));
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

            RemboursementResponse result = service.demander(reservationId, request);

            assertThat(result).isSameAs(response);
            Remboursement r = remboursementSauvegarde();
            assertThat(r.getStatut()).isEqualTo(StatutRemboursement.APPROVED);
            assertThat(r.getMontant()).isEqualByComparingTo("650.00");
            assertThat(r.getDecision()).isEqualTo("Remboursement intégral (100%)");
            assertThat(r.getMotif()).isEqualTo("Empechement de derniere minute");
            assertThat(r.getPaiement()).isSameAs(paiement);
            verify(paymentGateway).refund(montant, PAY_REF);
            verify(reservation).setStatut(StatutReservation.REFUNDED);
            verify(b1).setStatut(StatutBillet.CANCELLED);
            verify(b2).setStatut(StatutBillet.CANCELLED);
            verify(promotionRepository, never()).devrementUtilisation(any());
        }

        @Test
        void nominalAvecPromo_rendLUtilisationDeLaPromo() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(30 * 24, StatusEvenment.PUBLISHED), promotion());
            Paiement paiement = paiement(reservation);
            stubDemande(reservation, paiement, List.of(billet(StatutBillet.VALID)));
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

            service.demander(reservationId, request);

            verify(promotionRepository).devrementUtilisation(promoId);
        }

        @Test
        void unPeuPlusDe48hAvantLeDebut_estAccepte() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(49, StatusEvenment.PUBLISHED), null);
            Paiement paiement = paiement(reservation);
            stubDemande(reservation, paiement, List.of(billet(StatutBillet.VALID)));
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

            service.demander(reservationId, request);

            assertThat(remboursementSauvegarde().getStatut()).isEqualTo(StatutRemboursement.APPROVED);
        }

        @Test
        void moinsDe48h_estRefuse_sansToucherAuPaiementNiAuxBillets() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(47, StatusEvenment.PUBLISHED), null);
            Paiement paiement = paiement(reservation);
            Billet b1 = billet(StatutBillet.VALID);
            stubDemande(reservation, paiement, List.of(b1));

            RemboursementResponse result = service.demander(reservationId, request);

            assertThat(result).isSameAs(response);
            Remboursement r = remboursementSauvegarde();
            assertThat(r.getStatut()).isEqualTo(StatutRemboursement.REJECTED);
            assertThat(r.getMontant()).isEqualByComparingTo("0");
            assertThat(r.getDecision()).contains("48h");
            verifyNoInteractions(paymentGateway);
            verify(reservation, never()).setStatut(any());
            verify(b1, never()).setStatut(any());
            verify(promotionRepository, never()).devrementUtilisation(any());
        }

        @Test
        void billetDejaUtilise_estRefuse() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(30 * 24, StatusEvenment.PUBLISHED), null);
            Paiement paiement = paiement(reservation);
            Billet utilise = billet(StatutBillet.USED);
            Billet valide = billet(StatutBillet.VALID);
            stubDemande(reservation, paiement, List.of(utilise, valide));

            service.demander(reservationId, request);

            Remboursement r = remboursementSauvegarde();
            assertThat(r.getStatut()).isEqualTo(StatutRemboursement.REJECTED);
            assertThat(r.getDecision()).contains("déjà été utilisé");
            verifyNoInteractions(paymentGateway);
            verify(reservation, never()).setStatut(any());
        }

        @Test
        void moinsDe48hEtBilletUtilise_laRaisonDesDelaisPrime() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(10, StatusEvenment.PUBLISHED), null);
            Paiement paiement = paiement(reservation);
            stubDemande(reservation, paiement, List.of(billet(StatutBillet.USED)));

            service.demander(reservationId, request);

            assertThat(remboursementSauvegarde().getDecision()).contains("48h");
        }

        @Test
        void eventAnnule_rembourseMemeSiMoinsDe48hEtBilletUtilise() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(5, StatusEvenment.CANCELLED), null);
            Paiement paiement = paiement(reservation);
            Billet utilise = billet(StatutBillet.USED);
            stubDemande(reservation, paiement, List.of(utilise));
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

            service.demander(reservationId, request);

            assertThat(remboursementSauvegarde().getStatut()).isEqualTo(StatutRemboursement.APPROVED);
            verify(reservation).setStatut(StatutReservation.REFUNDED);
            verify(utilise).setStatut(StatutBillet.CANCELLED);
        }

        @Test
        void echecDuGateway_leveBusinessRule_etNeSauvegardeRien() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(30 * 24, StatusEvenment.PUBLISHED), promotion());
            Paiement paiement = paiement(reservation);
            Billet b1 = billet(StatutBillet.VALID);
            stubDemande(reservation, paiement, List.of(b1));
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementEchoue);

            assertThatThrownBy(() -> service.demander(reservationId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(remboursementRepository, never()).save(any());
            verify(reservation, never()).setStatut(any());
            verify(b1, never()).setStatut(any());
            verify(promotionRepository, never()).devrementUtilisation(any());
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
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(100, StatusEvenment.PUBLISHED), null);
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> service.getByReservation(reservationId, UUID.randomUUID()))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(remboursementRepository, never()).findByPaiementReservationIdOrderByDateDemandeDesc(any());
        }

        @Test
        void nominal_retourneLesRemboursementsMappes() {
            Reservation reservation = reservation(StatutReservation.REFUNDED, eventDans(100, StatusEvenment.PUBLISHED), null);
            Remboursement r1 = Remboursement.builder().statut(StatutRemboursement.REJECTED).build();
            Remboursement r2 = Remboursement.builder().statut(StatutRemboursement.APPROVED).build();
            RemboursementResponse resp1 = mock(RemboursementResponse.class);
            RemboursementResponse resp2 = mock(RemboursementResponse.class);
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(remboursementRepository.findByPaiementReservationIdOrderByDateDemandeDesc(reservationId))
                    .thenReturn(List.of(r2, r1));
            when(remboursementMapper.toResponse(r1)).thenReturn(resp1);
            when(remboursementMapper.toResponse(r2)).thenReturn(resp2);

            assertThat(service.getByReservation(reservationId, clientId)).containsExactly(resp2, resp1);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("rembourserAutomatiquement")
    class RembourserAutomatiquement {

        @Test
        void reservationIntrouvable_retourneFalse() {
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

            assertThat(service.rembourserAutomatiquement(reservationId)).isFalse();

            verifyNoInteractions(paymentGateway, remboursementRepository);
        }

        @ParameterizedTest
        @EnumSource(value = StatutReservation.class, mode = EnumSource.Mode.EXCLUDE, names = "CONFIRMED")
        void reservationPasConfirmee_retourneFalse(StatutReservation statut) {
            Reservation reservation = reservation(statut, eventDans(100, StatusEvenment.CANCELLED), null);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

            assertThat(service.rembourserAutomatiquement(reservationId)).isFalse();

            verifyNoInteractions(paymentGateway, remboursementRepository);
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, mode = EnumSource.Mode.EXCLUDE, names = "CANCELLED")
        void eventPasAnnule_retourneFalse(StatusEvenment statutEvent) {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(100, statutEvent), null);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

            assertThat(service.rembourserAutomatiquement(reservationId)).isFalse();

            verifyNoInteractions(paymentGateway, remboursementRepository);
        }

        @Test
        void paiementIntrouvable_leveResourceNotFound() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(100, StatusEvenment.CANCELLED), null);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
            when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rembourserAutomatiquement(reservationId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(paymentGateway);
        }

        @Test
        void nominal_rembourseTout_billetsUtilisesInclus() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(100, StatusEvenment.CANCELLED), null);
            Paiement paiement = paiement(reservation);
            Billet valide = billet(StatutBillet.VALID);
            Billet utilise = billet(StatutBillet.USED);
            stubDemande(reservation, paiement, List.of(valide, utilise));
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

            assertThat(service.rembourserAutomatiquement(reservationId)).isTrue();

            Remboursement r = remboursementSauvegarde();
            assertThat(r.getStatut()).isEqualTo(StatutRemboursement.APPROVED);
            assertThat(r.getMontant()).isEqualByComparingTo("650.00");
            assertThat(r.getMotif()).contains("Annulation");
            assertThat(r.getDecision()).contains("automatique");
            verify(reservation).setStatut(StatutReservation.REFUNDED);
            verify(valide).setStatut(StatutBillet.CANCELLED);
            verify(utilise).setStatut(StatutBillet.CANCELLED);
        }

        @Test
        void nominalAvecPromo_rendLUtilisation() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(100, StatusEvenment.CANCELLED), promotion());
            Paiement paiement = paiement(reservation);
            stubDemande(reservation, paiement, List.of(billet(StatutBillet.VALID)));
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

            assertThat(service.rembourserAutomatiquement(reservationId)).isTrue();

            verify(promotionRepository).devrementUtilisation(promoId);
        }

        @Test
        void echecDuGateway_leveBusinessRule_etNeSauvegardeRien() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(100, StatusEvenment.CANCELLED), null);
            Paiement paiement = paiement(reservation);
            stubDemande(reservation, paiement, List.of(billet(StatutBillet.VALID)));
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementEchoue);

            assertThatThrownBy(() -> service.rembourserAutomatiquement(reservationId))
                    .isInstanceOf(BusinessRuleException.class);

            verify(remboursementRepository, never()).save(any());
            verify(reservation, never()).setStatut(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("approuverExceptionnellement")
    class ApprouverExceptionnellement {

        private Remboursement rejete(Paiement paiement) {
            return Remboursement.builder()
                    .paiement(paiement)
                    .statut(StatutRemboursement.REJECTED)
                    .montant(BigDecimal.ZERO)
                    .decision("Refusé : la demande doit être faite au moins 48h avant le début de l'événement")
                    .build();
        }

        @Test
        void remboursementIntrouvable_leveResourceNotFound() {
            when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approuverExceptionnellement(remboursementId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(remboursementId.toString());

            verifyNoInteractions(paymentGateway);
        }

        @ParameterizedTest
        @EnumSource(value = StatutRemboursement.class, mode = EnumSource.Mode.EXCLUDE, names = "REJECTED")
        void statutDifferentDeRejected_leveBusinessRule(StatutRemboursement statut) {
            Remboursement remboursement = Remboursement.builder().statut(statut).build();
            when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));

            assertThatThrownBy(() -> service.approuverExceptionnellement(remboursementId))
                    .isInstanceOf(BusinessRuleException.class);

            verifyNoInteractions(paymentGateway, reservationRepository);
        }

        @Test
        void reservationIntrouvable_leveResourceNotFound() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(10, StatusEvenment.PUBLISHED), null);
            Paiement paiement = paiement(reservation);
            Remboursement remboursement = rejete(paiement);
            when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approuverExceptionnellement(remboursementId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(paymentGateway);
        }

        @ParameterizedTest
        @EnumSource(value = StatutReservation.class, mode = EnumSource.Mode.EXCLUDE, names = "CONFIRMED")
        void reservationPlusRemboursable_leveBusinessRule(StatutReservation statut) {
            Reservation reservation = reservation(statut, eventDans(10, StatusEvenment.PUBLISHED), null);
            Paiement paiement = paiement(reservation);
            Remboursement remboursement = rejete(paiement);
            when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> service.approuverExceptionnellement(remboursementId))
                    .isInstanceOf(BusinessRuleException.class);

            verifyNoInteractions(paymentGateway);
            assertThat(remboursement.getStatut()).isEqualTo(StatutRemboursement.REJECTED);
        }

        @Test
        void nominal_approuveEtRembourseIntegralement() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(10, StatusEvenment.PUBLISHED), promotion());
            Paiement paiement = paiement(reservation);
            Remboursement remboursement = rejete(paiement);
            Billet utilise = billet(StatutBillet.USED);
            when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
            when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(List.of(utilise));
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

            RemboursementResponse result = service.approuverExceptionnellement(remboursementId);

            assertThat(result).isSameAs(response);
            assertThat(remboursement.getStatut()).isEqualTo(StatutRemboursement.APPROVED);
            assertThat(remboursement.getMontant()).isEqualByComparingTo("650.00");
            assertThat(remboursement.getDecision()).contains("exceptionnellement");
            verify(reservation).setStatut(StatutReservation.REFUNDED);
            verify(utilise).setStatut(StatutBillet.CANCELLED);
            verify(promotionRepository).devrementUtilisation(promoId);
        }

        @Test
        void echecDuGateway_leveBusinessRule_etLeRemboursementResteRejete() {
            Reservation reservation = reservation(StatutReservation.CONFIRMED, eventDans(10, StatusEvenment.PUBLISHED), null);
            Paiement paiement = paiement(reservation);
            Remboursement remboursement = rejete(paiement);
            when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
            when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(List.of());
            when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementEchoue);

            assertThatThrownBy(() -> service.approuverExceptionnellement(remboursementId))
                    .isInstanceOf(BusinessRuleException.class);

            assertThat(remboursement.getStatut()).isEqualTo(StatutRemboursement.REJECTED);
            verify(reservation, never()).setStatut(any());
        }
    }
}