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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT) // pas d'erreur si un stub n'est pas utilise dans un test
class PaiementServiceTest {

    @Mock
    private PaiementRepository paiementRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private BilletRepository billetRepository;
    @Mock
    private PaiementMapper paiementMapper;
    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaiementService service;

    @Captor
    private ArgumentCaptor<List<Billet>> billetsCaptor;
    @Captor
    private ArgumentCaptor<String> referenceCaptor;

    private static final String KEY = "key-1";

    private final UUID reservationId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final BigDecimal total = new BigDecimal("650.00");

    private Client client;
    private PaiementRequest request;       // requete valide par defaut : 650.00, pas d'echec
    private PaymentResult chargeReussie;
    private PaymentResult chargeEchouee;
    private PaiementResponse response;

    @BeforeEach
    void setUp() {
        client = mock(Client.class);
        when(client.getId()).thenReturn(clientId);

        request = OneRequest("650.00", false);

        chargeReussie = mock(PaymentResult.class);
        when(chargeReussie.success()).thenReturn(true);
        chargeEchouee = mock(PaymentResult.class);
        when(chargeEchouee.success()).thenReturn(false);

        response = mock(PaiementResponse.class);
        when(paiementMapper.toResponse(any(Paiement.class))).thenReturn(response);
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(i -> i.getArgument(0));
        when(paiementRepository.existsByReference(anyString())).thenReturn(false);
    }

    // ---------- petits helpers (comme OneUser() avant) ----------
    // IMPORTANT : toujours les appeler dans une variable locale, jamais a l'interieur de when(...)
    private PaiementRequest OneRequest(String montant, Boolean simulerEchec) {
        PaiementRequest r = mock(PaiementRequest.class);
        when(r.idempotencyKey()).thenReturn(KEY);
        when(r.clientId()).thenReturn(clientId);
        when(r.montant()).thenReturn(new BigDecimal(montant));
        when(r.simulerEchec()).thenReturn(simulerEchec);
        return r;
    }

    private LigneReservation OneLigne(int quantite) {
        CategorieBillet categorie = mock(CategorieBillet.class);
        LigneReservation l = mock(LigneReservation.class);
        when(l.getQuantite()).thenReturn(quantite);
        when(l.getCategorieBillet()).thenReturn(categorie);
        return l;
    }

    private Event OneEvent(StatusEvenment statut) {
        return Event.builder().statut(statut).build();
    }

    private Reservation OneReservation(StatutReservation statut, Event event, LocalDateTime expiration,
                                       List<LigneReservation> lignes) {
        Reservation r = mock(Reservation.class);
        when(r.getId()).thenReturn(reservationId);
        when(r.getStatut()).thenReturn(statut);
        when(r.getClient()).thenReturn(client);
        when(r.getEvent()).thenReturn(event);
        when(r.getDateExpiration()).thenReturn(expiration);
        when(r.getMontantTotal()).thenReturn(total);
        when(r.getLignes()).thenReturn(lignes);
        return r;
    }

    // reservation payable : PENDING_PAYMENT, event publie, expire dans 10 min, 2 lignes (2 + 1 billets)
    private Reservation ReservationPayable() {
        List<LigneReservation> lignes = List.of(OneLigne(2), OneLigne(1));
        Event event = OneEvent(StatusEvenment.PUBLISHED);
        return OneReservation(StatutReservation.PENDING_PAYMENT, event, LocalDateTime.now().plusMinutes(10), lignes);
    }

    private void stubReservation(Reservation reservation) {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
    }

    // recupere le paiement envoye a save()
    private Paiement paiementSauvegarde() {
        ArgumentCaptor<Paiement> captor = ArgumentCaptor.forClass(Paiement.class);
        verify(paiementRepository).save(captor.capture());
        return captor.getValue();
    }

    // =====================================================================
    // payer : reservation / idempotence
    // =====================================================================
    @Test
    void payer_reservationIntrouvable_ResourceNotFound() {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.payer(reservationId, request));

        assertTrue(ex.getMessage().contains(reservationId.toString()));
        verifyNoInteractions(paiementRepository, paymentGateway, billetRepository);
    }

    @Test
    void payer_replayMemeReservationEtMemeMontant_retourneLaReponseSansRepayer() {
        // ARRANGE
        Reservation reservation = ReservationPayable();
        Paiement existant = mock(Paiement.class);
        when(existant.getReservation()).thenReturn(reservation);
        when(existant.getMontant()).thenReturn(new BigDecimal("650.0")); // echelle differente : on compare avec compareTo
        stubReservation(reservation);
        when(paiementRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existant));
        when(paiementMapper.toResponse(existant)).thenReturn(response);

        // ACT
        PaiementResponse result = service.payer(reservationId, request);

        // ASSERT : meme reponse, aucun nouveau paiement
        assertSame(response, result);
        verifyNoInteractions(paymentGateway, billetRepository);
        verify(paiementRepository, never()).save(any());
    }

    @Test
    void payer_replayAvecUneAutreReservation_IdempotencyConflict() {
        Reservation reservation = ReservationPayable();
        Reservation autre = mock(Reservation.class);
        when(autre.getId()).thenReturn(UUID.randomUUID());
        Paiement existant = mock(Paiement.class);
        when(existant.getReservation()).thenReturn(autre);
        stubReservation(reservation);
        when(paiementRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existant));

        assertThrows(IdempotencyConflictException.class, () -> service.payer(reservationId, request));

        verifyNoInteractions(paymentGateway);
        verify(paiementRepository, never()).save(any());
    }

    @Test
    void payer_replayAvecUnAutreMontant_IdempotencyConflict() {
        Reservation reservation = ReservationPayable();
        Paiement existant = mock(Paiement.class);
        when(existant.getReservation()).thenReturn(reservation);
        when(existant.getMontant()).thenReturn(new BigDecimal("100.00"));
        stubReservation(reservation);
        when(paiementRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existant));

        assertThrows(IdempotencyConflictException.class, () -> service.payer(reservationId, request));

        verifyNoInteractions(paymentGateway);
    }

    // =====================================================================
    // payer : checks
    // =====================================================================
    @Test
    void payer_pasLeProprietaire_Forbidden() {
        Reservation reservation = ReservationPayable();
        stubReservation(reservation);
        PaiementRequest autreClient = mock(PaiementRequest.class);
        when(autreClient.idempotencyKey()).thenReturn(KEY);
        when(autreClient.clientId()).thenReturn(UUID.randomUUID()); // un autre client

        assertThrows(ForbiddenOperationException.class, () -> service.payer(reservationId, autreClient));

        verifyNoInteractions(paymentGateway, billetRepository);
        verify(paiementRepository, never()).save(any());
    }

    @Test
    void payer_reservationExpired_ReservationExpired() {
        Reservation reservation = OneReservation(StatutReservation.EXPIRED, OneEvent(StatusEvenment.PUBLISHED),
                LocalDateTime.now().minusMinutes(5), List.of());
        stubReservation(reservation);

        assertThrows(ReservationExpiredException.class, () -> service.payer(reservationId, request));

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void payer_statutConfirmed_BusinessRule() {
        payerStatutNonPayable(StatutReservation.CONFIRMED);
    }

    @Test
    void payer_statutCancelled_BusinessRule() {
        payerStatutNonPayable(StatutReservation.CANCELLED);
    }

    @Test
    void payer_statutRefunded_BusinessRule() {
        payerStatutNonPayable(StatutReservation.REFUNDED);
    }

    @Test
    void payer_eventAnnule_BusinessRule() {
        Reservation reservation = OneReservation(StatutReservation.PENDING_PAYMENT, OneEvent(StatusEvenment.CANCELLED),
                LocalDateTime.now().plusMinutes(10), List.of());
        stubReservation(reservation);

        assertThrows(BusinessRuleException.class, () -> service.payer(reservationId, request));

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void payer_dateExpirationDepassee_ReservationExpired() {
        Reservation reservation = OneReservation(StatutReservation.PENDING_PAYMENT, OneEvent(StatusEvenment.PUBLISHED),
                LocalDateTime.now().minusMinutes(1), List.of());
        stubReservation(reservation);

        assertThrows(ReservationExpiredException.class, () -> service.payer(reservationId, request));

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void payer_mauvaisMontant_BusinessRule_sansCharger() {
        Reservation reservation = ReservationPayable();
        stubReservation(reservation);
        PaiementRequest mauvais = OneRequest("100.00", false);

        assertThrows(BusinessRuleException.class, () -> service.payer(reservationId, mauvais));

        verifyNoInteractions(paymentGateway, billetRepository);
        verify(paiementRepository, never()).save(any());
    }

    // =====================================================================
    // payer : succes
    // =====================================================================
    @Test
    void payer_succes_confirmeLaReservation_etSauvegardeLePaiement() {
        // ARRANGE
        Reservation reservation = ReservationPayable();
        stubReservation(reservation);
        when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

        // ACT
        PaiementResponse result = service.payer(reservationId, request);

        // ASSERT
        assertSame(response, result);
        Paiement p = paiementSauvegarde();
        assertEquals(StatutPaiement.SUCCESS, p.getStatut());
        assertEquals(0, p.getMontant().compareTo(new BigDecimal("650.00")));
        assertEquals(KEY, p.getIdempotencyKey());
        assertTrue(p.getReference().startsWith("PAY-"));
        assertNotNull(p.getDatePaiement());
        assertSame(reservation, p.getReservation());
        verify(reservation).setStatut(StatutReservation.CONFIRMED);
        // la reference envoyee au gateway est celle du paiement
        verify(paymentGateway).charge(eq(total), referenceCaptor.capture(), eq(false));
        assertEquals(p.getReference(), referenceCaptor.getValue());
    }

    @Test
    void payer_succes_genereUnBilletParPlace_toutesVALID_avecCodesUniques() {
        // ARRANGE : lignes 2 + 1 = 3 billets
        Reservation reservation = ReservationPayable();
        stubReservation(reservation);
        when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

        // ACT
        service.payer(reservationId, request);

        // ASSERT
        verify(billetRepository).saveAll(billetsCaptor.capture());
        List<Billet> billets = billetsCaptor.getValue();
        assertEquals(3, billets.size());
        Set<String> codes = new HashSet<>();
        for (Billet b : billets) {
            assertEquals(StatutBillet.VALID, b.getStatut());
            assertTrue(b.getCode().startsWith("BIL-"));
            assertNotNull(b.getDateEmission());
            codes.add(b.getCode());
        }
        assertEquals(3, codes.size()); // aucun code en double
    }

    @Test
    void payer_montantEgalAvecUneAutreEchelle_estAccepte() {
        Reservation reservation = ReservationPayable();
        stubReservation(reservation);
        PaiementRequest r = OneRequest("650", false); // 650 vs 650.00
        when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

        service.payer(reservationId, r);

        assertEquals(StatutPaiement.SUCCESS, paiementSauvegarde().getStatut());
    }

    @Test
    void payer_simulerEchecNull_estTraiteCommeFalse() {
        Reservation reservation = ReservationPayable();
        stubReservation(reservation);
        PaiementRequest r = OneRequest("650.00", null);
        when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

        service.payer(reservationId, r);

        verify(paymentGateway).charge(eq(total), anyString(), eq(false));
    }

    @Test
    void payer_referenceDejaExistante_enRegenereUneAutre() {
        Reservation reservation = ReservationPayable();
        stubReservation(reservation);
        when(paiementRepository.existsByReference(anyString())).thenReturn(true, false);
        when(paymentGateway.charge(eq(total), anyString(), anyBoolean())).thenReturn(chargeReussie);

        service.payer(reservationId, request);

        verify(paiementRepository, times(2)).existsByReference(anyString());
    }

    // =====================================================================
    // payer : echec
    // =====================================================================
    @Test
    void payer_echecSimule_paiementFailed_reservationEtBilletsInchanges() {
        // ARRANGE
        Reservation reservation = ReservationPayable();
        stubReservation(reservation);
        PaiementRequest r = OneRequest("650.00", true);
        when(paymentGateway.charge(eq(total), anyString(), eq(true))).thenReturn(chargeEchouee);

        // ACT
        PaiementResponse result = service.payer(reservationId, r);

        // ASSERT
        assertSame(response, result);
        assertEquals(StatutPaiement.FAILED, paiementSauvegarde().getStatut());
        verify(reservation, never()).setStatut(any()); // reste PENDING_PAYMENT
        verify(billetRepository, never()).saveAll(any());
    }

    @Test
    void payer_reessaiApresUnEchec_reutiliseLePaiementExistant() {
        // ARRANGE : un paiement FAILED existe deja pour cette reservation
        Reservation reservation = ReservationPayable();
        Paiement echoue = Paiement.builder().reservation(reservation).statut(StatutPaiement.FAILED).build();
        stubReservation(reservation);
        when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.of(echoue));
        when(paymentGateway.charge(eq(total), anyString(), eq(false))).thenReturn(chargeReussie);

        // ACT
        service.payer(reservationId, request);

        // ASSERT : meme objet, maintenant SUCCESS
        Paiement sauvegarde = paiementSauvegarde();
        assertSame(echoue, sauvegarde);
        assertEquals(StatutPaiement.SUCCESS, sauvegarde.getStatut());
        verify(reservation).setStatut(StatutReservation.CONFIRMED);
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
        Reservation reservation = ReservationPayable();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        assertThrows(ForbiddenOperationException.class,
                () -> service.getByReservation(reservationId, UUID.randomUUID()));

        verify(paiementRepository, never()).findByReservationId(any());
    }

    @Test
    void getByReservation_aucunPaiement_ResourceNotFound() {
        Reservation reservation = ReservationPayable();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getByReservation(reservationId, clientId));
    }

    @Test
    void getByReservation_nominal_retourneLePaiement() {
        Reservation reservation = ReservationPayable();
        Paiement paiement = Paiement.builder().reservation(reservation).build();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(paiementRepository.findByReservationId(reservationId)).thenReturn(Optional.of(paiement));
        when(paiementMapper.toResponse(paiement)).thenReturn(response);

        assertSame(response, service.getByReservation(reservationId, clientId));
    }

    // =====================================================================
    // methode utilitaire (evite de repeter le meme code dans plusieurs tests)
    // =====================================================================
    private void payerStatutNonPayable(StatutReservation statut) {
        Reservation reservation = OneReservation(statut, OneEvent(StatusEvenment.PUBLISHED),
                LocalDateTime.now().plusMinutes(10), List.of());
        stubReservation(reservation);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.payer(reservationId, request));

        assertTrue(ex.getMessage().contains(statut.name())); // le message cite le statut
        verifyNoInteractions(paymentGateway);
    }
}