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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT) // pas d'erreur si un stub n'est pas utilise dans un test
class RemboursementServiceTest {

    @Mock
    private RemboursementRepository remboursementRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private PaiementRepository paiementRepository;
    @Mock
    private BilletRepository billetRepository;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private RemboursementMapper remboursementMapper;

    @InjectMocks
    private RemboursementService service;

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
        when(client.getId()).thenReturn(clientId);

        request = mock(RemboursementRequest.class);
        when(request.clientId()).thenReturn(clientId);
        when(request.motif()).thenReturn("Empechement de derniere minute");

        paiementReussi = mock(PaymentResult.class);
        when(paiementReussi.success()).thenReturn(true);
        paiementEchoue = mock(PaymentResult.class);
        when(paiementEchoue.success()).thenReturn(false);

        response = mock(RemboursementResponse.class);
        when(remboursementMapper.toResponse(any(Remboursement.class))).thenReturn(response);
        when(remboursementRepository.save(any(Remboursement.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- petits helpers (comme OneUser() avant) ----------
    // IMPORTANT : toujours les appeler dans une variable locale, jamais a l'interieur de when(...)

    // event dont le debut est dans "heures" heures
    private Event EventDans(long heures, StatusEvenment statut) {
        return Event.builder().statut(statut).dateDebut(LocalDateTime.now().plusHours(heures)).build();
    }

    private Reservation OneReservation(StatutReservation statut, Event event, Promotion promotion) {
        Reservation r = mock(Reservation.class);
        when(r.getId()).thenReturn(reservationId);
        when(r.getStatut()).thenReturn(statut);
        when(r.getClient()).thenReturn(client);
        when(r.getEvent()).thenReturn(event);
        when(r.getPromotion()).thenReturn(promotion);
        return r;
    }

    private Paiement OnePaiement(Reservation reservation) {
        Paiement p = mock(Paiement.class);
        when(p.getId()).thenReturn(paiementId);
        when(p.getMontant()).thenReturn(montant);
        when(p.getReference()).thenReturn(PAY_REF);
        when(p.getReservation()).thenReturn(reservation);
        return p;
    }

    private Billet OneBillet(StatutBillet statut) {
        Billet b = mock(Billet.class);
        when(b.getStatut()).thenReturn(statut);
        return b;
    }

    private Promotion OnePromotion() {
        return Promotion.builder().id(promoId).build();
    }

    // un remboursement deja refuse (pour approuverExceptionnellement)
    private Remboursement OneRejete(Paiement paiement) {
        return Remboursement.builder().paiement(paiement).statut(StatutRemboursement.REJECTED)
                .montant(BigDecimal.ZERO)
                .decision("Refusé : la demande doit être faite au moins 48h avant le début de l'événement").build();
    }

    // branche reservation / paiement / billets pour demander() et rembourserAutomatiquement()
    private void stubDemande(Reservation reservation, Paiement paiement, List<Billet> billets) {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.of(paiement));
        when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(billets);
    }

    // recupere le remboursement envoye a save()
    private Remboursement remboursementSauvegarde() {
        ArgumentCaptor<Remboursement> captor = ArgumentCaptor.forClass(Remboursement.class);
        verify(remboursementRepository).save(captor.capture());
        return captor.getValue();
    }

    // =====================================================================
    // demander : erreurs
    // =====================================================================
    @Test
    void demander_reservationIntrouvable_ResourceNotFound() {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.demander(reservationId, request));

        assertTrue(ex.getMessage().contains(reservationId.toString()));
        verifyNoInteractions(paiementRepository, paymentGateway);
    }

    @Test
    void demander_pasLeProprietaire_Forbidden() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(100, StatusEvenment.PUBLISHED), null);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        RemboursementRequest autre = mock(RemboursementRequest.class);
        when(autre.clientId()).thenReturn(UUID.randomUUID());

        assertThrows(ForbiddenOperationException.class, () -> service.demander(reservationId, autre));

        verifyNoInteractions(paymentGateway);
        verify(remboursementRepository, never()).save(any());
    }

    @Test
    void demander_dejaRemboursee_BusinessRule() {
        Reservation reservation = OneReservation(StatutReservation.REFUNDED, EventDans(100, StatusEvenment.PUBLISHED), null);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.demander(reservationId, request));

        assertTrue(ex.getMessage().contains("déjà été remboursée"));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void demander_reservationPendingPayment_BusinessRule() {
        demanderReservationNonPayee(StatutReservation.PENDING_PAYMENT);
    }

    @Test
    void demander_reservationExpired_BusinessRule() {
        demanderReservationNonPayee(StatutReservation.EXPIRED);
    }

    @Test
    void demander_reservationCancelled_BusinessRule() {
        demanderReservationNonPayee(StatutReservation.CANCELLED);
    }

    @Test
    void demander_paiementIntrouvable_ResourceNotFound() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(100, StatusEvenment.PUBLISHED), null);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.demander(reservationId, request));

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void demander_refusDejaEnregistre_retourneLaMemeReponse_sansRienRefaire() {
        // ARRANGE : un refus existe deja pour ce paiement
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(10, StatusEvenment.PUBLISHED), null);
        Paiement paiement = OnePaiement(reservation);
        Remboursement dejaRefuse = Remboursement.builder().statut(StatutRemboursement.REJECTED).build();
        RemboursementResponse existante = mock(RemboursementResponse.class);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.of(paiement));
        when(remboursementRepository.findFirstByPaiementIdAndStatut(paiementId, StatutRemboursement.REJECTED))
                .thenReturn(Optional.of(dejaRefuse));
        when(remboursementMapper.toResponse(dejaRefuse)).thenReturn(existante);

        // ACT
        RemboursementResponse result = service.demander(reservationId, request);

        // ASSERT
        assertSame(existante, result);
        verify(remboursementRepository, never()).save(any());
        verifyNoInteractions(paymentGateway, billetRepository);
    }

    // =====================================================================
    // demander : remboursement accepte
    // =====================================================================
    @Test
    void demander_nominalPlusDe48h_rembourseIntegralement() {
        // ARRANGE : l'event commence dans 30 jours
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(30 * 24, StatusEvenment.PUBLISHED), null);
        Paiement paiement = OnePaiement(reservation);
        Billet b1 = OneBillet(StatutBillet.VALID);
        Billet b2 = OneBillet(StatutBillet.VALID);
        stubDemande(reservation, paiement, List.of(b1, b2));
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

        // ACT
        RemboursementResponse result = service.demander(reservationId, request);

        // ASSERT
        assertSame(response, result);
        Remboursement r = remboursementSauvegarde();
        assertEquals(StatutRemboursement.APPROVED, r.getStatut());
        assertEquals(0, r.getMontant().compareTo(new BigDecimal("650.00")));
        assertEquals("Remboursement intégral (100%)", r.getDecision());
        assertEquals("Empechement de derniere minute", r.getMotif());
        assertSame(paiement, r.getPaiement());
        verify(paymentGateway).refund(montant, PAY_REF);
        verify(reservation).setStatut(StatutReservation.REFUNDED);
        verify(b1).setStatut(StatutBillet.CANCELLED);
        verify(b2).setStatut(StatutBillet.CANCELLED);
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    @Test
    void demander_nominalAvecPromo_rendLUtilisationDeLaPromo() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(30 * 24, StatusEvenment.PUBLISHED), OnePromotion());
        Paiement paiement = OnePaiement(reservation);
        stubDemande(reservation, paiement, List.of(OneBillet(StatutBillet.VALID)));
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

        service.demander(reservationId, request);

        verify(promotionRepository).devrementUtilisation(promoId);
    }

    @Test
    void demander_unPeuPlusDe48hAvantLeDebut_estAccepte() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(49, StatusEvenment.PUBLISHED), null);
        Paiement paiement = OnePaiement(reservation);
        stubDemande(reservation, paiement, List.of(OneBillet(StatutBillet.VALID)));
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

        service.demander(reservationId, request);

        assertEquals(StatutRemboursement.APPROVED, remboursementSauvegarde().getStatut());
    }

    @Test
    void demander_eventAnnule_rembourseMemeSiMoinsDe48hEtBilletUtilise() {
        // event annule : pas de regle des 48h, pas de refus pour billet utilise
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(5, StatusEvenment.CANCELLED), null);
        Paiement paiement = OnePaiement(reservation);
        Billet utilise = OneBillet(StatutBillet.USED);
        stubDemande(reservation, paiement, List.of(utilise));
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

        service.demander(reservationId, request);

        assertEquals(StatutRemboursement.APPROVED, remboursementSauvegarde().getStatut());
        verify(reservation).setStatut(StatutReservation.REFUNDED);
        verify(utilise).setStatut(StatutBillet.CANCELLED);
    }

    // =====================================================================
    // demander : remboursement refuse
    // =====================================================================
    @Test
    void demander_moinsDe48h_estRefuse_sansToucherAuPaiementNiAuxBillets() {
        // ARRANGE : l'event commence dans 47h
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(47, StatusEvenment.PUBLISHED), null);
        Paiement paiement = OnePaiement(reservation);
        Billet b1 = OneBillet(StatutBillet.VALID);
        stubDemande(reservation, paiement, List.of(b1));

        // ACT
        RemboursementResponse result = service.demander(reservationId, request);

        // ASSERT
        assertSame(response, result);
        Remboursement r = remboursementSauvegarde();
        assertEquals(StatutRemboursement.REJECTED, r.getStatut());
        assertEquals(0, r.getMontant().compareTo(BigDecimal.ZERO));
        assertTrue(r.getDecision().contains("48h"));
        verifyNoInteractions(paymentGateway);
        verify(reservation, never()).setStatut(any());
        verify(b1, never()).setStatut(any());
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    @Test
    void demander_billetDejaUtilise_estRefuse() {
        // ARRANGE : plus de 48h mais un billet est USED
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(30 * 24, StatusEvenment.PUBLISHED), null);
        Paiement paiement = OnePaiement(reservation);
        Billet utilise = OneBillet(StatutBillet.USED);
        Billet valide = OneBillet(StatutBillet.VALID);
        stubDemande(reservation, paiement, List.of(utilise, valide));

        // ACT
        service.demander(reservationId, request);

        // ASSERT
        Remboursement r = remboursementSauvegarde();
        assertEquals(StatutRemboursement.REJECTED, r.getStatut());
        assertTrue(r.getDecision().contains("déjà été utilisé"));
        verifyNoInteractions(paymentGateway);
        verify(reservation, never()).setStatut(any());
    }

    @Test
    void demander_moinsDe48hEtBilletUtilise_laRaisonDesDelaisPrime() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(10, StatusEvenment.PUBLISHED), null);
        Paiement paiement = OnePaiement(reservation);
        stubDemande(reservation, paiement, List.of(OneBillet(StatutBillet.USED)));

        service.demander(reservationId, request);

        assertTrue(remboursementSauvegarde().getDecision().contains("48h"));
    }

    @Test
    void demander_echecDuGateway_BusinessRule_etNeSauvegardeRien() {
        // ARRANGE : le gateway refuse le remboursement
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(30 * 24, StatusEvenment.PUBLISHED), OnePromotion());
        Paiement paiement = OnePaiement(reservation);
        Billet b1 = OneBillet(StatutBillet.VALID);
        stubDemande(reservation, paiement, List.of(b1));
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementEchoue);

        // ACT + ASSERT
        assertThrows(BusinessRuleException.class, () -> service.demander(reservationId, request));

        verify(remboursementRepository, never()).save(any());
        verify(reservation, never()).setStatut(any());
        verify(b1, never()).setStatut(any());
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    // =====================================================================
    // getByReservation
    // =====================================================================
    @Test
    void getByReservation_reservationIntrouvable_ResourceNotFound() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getByReservation(reservationId, clientId));
    }

    @Test
    void getByReservation_pasLeProprietaire_Forbidden() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(100, StatusEvenment.PUBLISHED), null);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        assertThrows(ForbiddenOperationException.class,
                () -> service.getByReservation(reservationId, UUID.randomUUID()));

        verify(remboursementRepository, never()).findByPaiementReservationIdOrderByDateDemandeDesc(any());
    }

    @Test
    void getByReservation_nominal_retourneLesRemboursementsMappes() {
        // ARRANGE
        Reservation reservation = OneReservation(StatutReservation.REFUNDED, EventDans(100, StatusEvenment.PUBLISHED), null);
        Remboursement r1 = Remboursement.builder().statut(StatutRemboursement.REJECTED).build();
        Remboursement r2 = Remboursement.builder().statut(StatutRemboursement.APPROVED).build();
        RemboursementResponse resp1 = mock(RemboursementResponse.class);
        RemboursementResponse resp2 = mock(RemboursementResponse.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(remboursementRepository.findByPaiementReservationIdOrderByDateDemandeDesc(reservationId))
                .thenReturn(List.of(r2, r1));
        when(remboursementMapper.toResponse(r1)).thenReturn(resp1);
        when(remboursementMapper.toResponse(r2)).thenReturn(resp2);

        // ACT
        List<RemboursementResponse> result = service.getByReservation(reservationId, clientId);

        // ASSERT : l'ordre du repository est conserve (r2 puis r1)
        assertEquals(2, result.size());
        assertSame(resp2, result.get(0));
        assertSame(resp1, result.get(1));
    }

    // =====================================================================
    // rembourserAutomatiquement
    // =====================================================================
    @Test
    void auto_reservationIntrouvable_retourneFalse() {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

        assertFalse(service.rembourserAutomatiquement(reservationId));

        verifyNoInteractions(paymentGateway, remboursementRepository);
    }

    @Test
    void auto_reservationPendingPayment_retourneFalse() {
        autoReservationPasConfirmee(StatutReservation.PENDING_PAYMENT);
    }

    @Test
    void auto_reservationExpired_retourneFalse() {
        autoReservationPasConfirmee(StatutReservation.EXPIRED);
    }

    @Test
    void auto_reservationCancelled_retourneFalse() {
        autoReservationPasConfirmee(StatutReservation.CANCELLED);
    }

    @Test
    void auto_reservationRefunded_retourneFalse() {
        autoReservationPasConfirmee(StatutReservation.REFUNDED);
    }

    @Test
    void auto_eventDraft_retourneFalse() {
        autoEventPasAnnule(StatusEvenment.DRAFT);
    }

    @Test
    void auto_eventPublished_retourneFalse() {
        autoEventPasAnnule(StatusEvenment.PUBLISHED);
    }

    @Test
    void auto_eventCompleted_retourneFalse() {
        autoEventPasAnnule(StatusEvenment.COMPLETED);
    }

    @Test
    void auto_paiementIntrouvable_ResourceNotFound() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(100, StatusEvenment.CANCELLED), null);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.rembourserAutomatiquement(reservationId));

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void auto_nominal_rembourseTout_billetsUtilisesInclus() {
        // ARRANGE : event annule, un billet VALID et un billet USED
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(100, StatusEvenment.CANCELLED), null);
        Paiement paiement = OnePaiement(reservation);
        Billet valide = OneBillet(StatutBillet.VALID);
        Billet utilise = OneBillet(StatutBillet.USED);
        stubDemande(reservation, paiement, List.of(valide, utilise));
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

        // ACT
        boolean result = service.rembourserAutomatiquement(reservationId);

        // ASSERT
        assertTrue(result);
        Remboursement r = remboursementSauvegarde();
        assertEquals(StatutRemboursement.APPROVED, r.getStatut());
        assertEquals(0, r.getMontant().compareTo(new BigDecimal("650.00")));
        assertTrue(r.getMotif().contains("Annulation"));
        assertTrue(r.getDecision().contains("automatique"));
        verify(reservation).setStatut(StatutReservation.REFUNDED);
        verify(valide).setStatut(StatutBillet.CANCELLED);
        verify(utilise).setStatut(StatutBillet.CANCELLED);
    }

    @Test
    void auto_nominalAvecPromo_rendLUtilisation() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(100, StatusEvenment.CANCELLED), OnePromotion());
        Paiement paiement = OnePaiement(reservation);
        stubDemande(reservation, paiement, List.of(OneBillet(StatutBillet.VALID)));
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

        assertTrue(service.rembourserAutomatiquement(reservationId));

        verify(promotionRepository).devrementUtilisation(promoId);
    }

    @Test
    void auto_echecDuGateway_BusinessRule_etNeSauvegardeRien() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(100, StatusEvenment.CANCELLED), null);
        Paiement paiement = OnePaiement(reservation);
        stubDemande(reservation, paiement, List.of(OneBillet(StatutBillet.VALID)));
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementEchoue);

        assertThrows(BusinessRuleException.class, () -> service.rembourserAutomatiquement(reservationId));

        verify(remboursementRepository, never()).save(any());
        verify(reservation, never()).setStatut(any());
    }

    // =====================================================================
    // approuverExceptionnellement
    // =====================================================================
    @Test
    void approuver_remboursementIntrouvable_ResourceNotFound() {
        when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.approuverExceptionnellement(remboursementId));

        assertTrue(ex.getMessage().contains(remboursementId.toString()));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void approuver_statutPending_BusinessRule() {
        approuverStatutNonRejete(StatutRemboursement.PENDING);
    }

    @Test
    void approuver_statutApproved_BusinessRule() {
        approuverStatutNonRejete(StatutRemboursement.APPROVED);
    }

    @Test
    void approuver_reservationIntrouvable_ResourceNotFound() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(10, StatusEvenment.PUBLISHED), null);
        Paiement paiement = OnePaiement(reservation);
        Remboursement remboursement = OneRejete(paiement);
        when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.approuverExceptionnellement(remboursementId));

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void approuver_reservationPendingPayment_BusinessRule() {
        approuverReservationPlusRemboursable(StatutReservation.PENDING_PAYMENT);
    }

    @Test
    void approuver_reservationExpired_BusinessRule() {
        approuverReservationPlusRemboursable(StatutReservation.EXPIRED);
    }

    @Test
    void approuver_reservationCancelled_BusinessRule() {
        approuverReservationPlusRemboursable(StatutReservation.CANCELLED);
    }

    @Test
    void approuver_reservationRefunded_BusinessRule() {
        approuverReservationPlusRemboursable(StatutReservation.REFUNDED);
    }

    @Test
    void approuver_nominal_approuveEtRembourseIntegralement() {
        // ARRANGE : un refus existe, l'admin l'approuve
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(10, StatusEvenment.PUBLISHED), OnePromotion());
        Paiement paiement = OnePaiement(reservation);
        Remboursement remboursement = OneRejete(paiement);
        Billet utilise = OneBillet(StatutBillet.USED);
        when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(List.of(utilise));
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementReussi);

        // ACT
        RemboursementResponse result = service.approuverExceptionnellement(remboursementId);

        // ASSERT
        assertSame(response, result);
        assertEquals(StatutRemboursement.APPROVED, remboursement.getStatut());
        assertEquals(0, remboursement.getMontant().compareTo(new BigDecimal("650.00")));
        assertTrue(remboursement.getDecision().contains("exceptionnellement"));
        verify(reservation).setStatut(StatutReservation.REFUNDED);
        verify(utilise).setStatut(StatutBillet.CANCELLED);
        verify(promotionRepository).devrementUtilisation(promoId);
    }

    @Test
    void approuver_echecDuGateway_BusinessRule_etLeRemboursementResteRejete() {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(10, StatusEvenment.PUBLISHED), null);
        Paiement paiement = OnePaiement(reservation);
        Remboursement remboursement = OneRejete(paiement);
        when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(billetRepository.findByLigneReservationReservationId(reservationId)).thenReturn(List.of());
        when(paymentGateway.refund(montant, PAY_REF)).thenReturn(paiementEchoue);

        assertThrows(BusinessRuleException.class, () -> service.approuverExceptionnellement(remboursementId));

        assertEquals(StatutRemboursement.REJECTED, remboursement.getStatut());
        verify(reservation, never()).setStatut(any());
    }

    // =====================================================================
    // methodes utilitaires (evitent de repeter le meme code dans plusieurs tests)
    // =====================================================================
    private void demanderReservationNonPayee(StatutReservation statut) {
        Reservation reservation = OneReservation(statut, EventDans(100, StatusEvenment.PUBLISHED), null);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.demander(reservationId, request));

        assertTrue(ex.getMessage().contains(statut.name())); // le message cite le statut
        verify(remboursementRepository, never()).save(any());
        verifyNoInteractions(paymentGateway);
    }

    private void autoReservationPasConfirmee(StatutReservation statut) {
        Reservation reservation = OneReservation(statut, EventDans(100, StatusEvenment.CANCELLED), null);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertFalse(service.rembourserAutomatiquement(reservationId));

        verifyNoInteractions(paymentGateway, remboursementRepository);
    }

    private void autoEventPasAnnule(StatusEvenment statutEvent) {
        Reservation reservation = OneReservation(StatutReservation.CONFIRMED, EventDans(100, statutEvent), null);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertFalse(service.rembourserAutomatiquement(reservationId));

        verifyNoInteractions(paymentGateway, remboursementRepository);
    }

    private void approuverStatutNonRejete(StatutRemboursement statut) {
        Remboursement remboursement = Remboursement.builder().statut(statut).build();
        when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));

        assertThrows(BusinessRuleException.class, () -> service.approuverExceptionnellement(remboursementId));

        verifyNoInteractions(paymentGateway, reservationRepository);
    }

    private void approuverReservationPlusRemboursable(StatutReservation statut) {
        Reservation reservation = OneReservation(statut, EventDans(10, StatusEvenment.PUBLISHED), null);
        Paiement paiement = OnePaiement(reservation);
        Remboursement remboursement = OneRejete(paiement);
        when(remboursementRepository.findById(remboursementId)).thenReturn(Optional.of(remboursement));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThrows(BusinessRuleException.class, () -> service.approuverExceptionnellement(remboursementId));

        verifyNoInteractions(paymentGateway);
        assertEquals(StatutRemboursement.REJECTED, remboursement.getStatut()); // reste refuse
    }
}