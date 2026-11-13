package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.ligneReservation.LigneReservationRequest;
import com.eventPro.eventPro.dto.reservation.ReservationRequest;
import com.eventPro.eventPro.dto.reservation.ReservationResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.reservation.ReservationMapper;
import com.eventPro.eventPro.model.CategorieBillet;
import com.eventPro.eventPro.model.Client;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.Promotion;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.model.enums.TypeReduction;
import com.eventPro.eventPro.repository.CategorieBilletRepository;
import com.eventPro.eventPro.repository.ClientRepository;
import com.eventPro.eventPro.repository.EventRepository;
import com.eventPro.eventPro.repository.LigneReservationRepository;
import com.eventPro.eventPro.repository.PromotionRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT) // pas d'erreur si un stub n'est pas utilise dans un test
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private LigneReservationRepository ligneReservationRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private CategorieBilletRepository categorieBilletRepository;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private ReservationMapper reservationMapper;

    @InjectMocks
    private ReservationService service;

    private final UUID clientId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID vipId = UUID.randomUUID();
    private final UUID stdId = UUID.randomUUID();
    private final UUID promoId = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();

    private final LocalDateTime now = LocalDateTime.now();
    private final LocalDate today = LocalDate.now();

    private Client client;
    private Event event;
    private CategorieBillet vip;   // 500 MAD, 10 places, quota 4
    private CategorieBillet std;   // 150 MAD, 100 places, quota 6
    private ReservationResponse response;

    @BeforeEach
    void setUp() {
        // @Value("${app.reservation.hold-minutes}") n'est pas rempli sans Spring : on le force a 15
        ReflectionTestUtils.setField(service, "holdMinutes", 15L);

        client = mock(Client.class);
        when(client.getId()).thenReturn(clientId);

        event = Event.builder().id(eventId).statut(StatusEvenment.PUBLISHED)
                .dateOuvertureVente(now.minusDays(1)).dateClotureVente(now.plusDays(10)).build();

        vip = CategorieBillet.builder().id(vipId).nom("VIP")
                .prixUnitaire(new BigDecimal("500.00")).quantiteTotale(10L).quotaMaxParClient(4).event(event).build();
        std = CategorieBillet.builder().id(stdId).nom("Standard")
                .prixUnitaire(new BigDecimal("150.00")).quantiteTotale(100L).quotaMaxParClient(6).event(event).build();

        response = mock(ReservationResponse.class);

        // environnement "tout va bien" pour create() : chaque test change juste ce qu'il veut tester
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        // retourne seulement les categories demandees (vip / std) selon la liste d'ids
        when(categorieBilletRepository.findAllByIdForUpdate(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return List.of(vip, std).stream().filter(c -> ids.contains(c.getId())).toList();
        });
        stockEtQuota(vip, 0, 0);
        stockEtQuota(std, 0, 0);
        when(reservationRepository.existsByReference(anyString())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(reservationMapper.toResponse(any(Reservation.class))).thenReturn(response);
    }

    // ---------- petits helpers (comme OneUser() avant) ----------
    private LigneReservationRequest OneLigne(UUID categorieId, int quantite) {
        LigneReservationRequest l = mock(LigneReservationRequest.class);
        when(l.categorieBilletId()).thenReturn(categorieId);
        when(l.quantite()).thenReturn(quantite);
        return l;
    }

    private ReservationRequest OneRequest(String promoCode, LigneReservationRequest... lignes) {
        ReservationRequest r = mock(ReservationRequest.class);
        when(r.clientId()).thenReturn(clientId);
        when(r.promoCode()).thenReturn(promoCode);
        when(r.lignes()).thenReturn(List.of(lignes));
        return r;
    }

    private Promotion OnePromo(TypeReduction type, String valeur, LocalDate debut, LocalDate fin, int nb, int max) {
        return Promotion.builder().id(promoId).code("TEST10").typeReduction(type)
                .valeurReduction(new BigDecimal(valeur)).dateDebut(debut).dateFin(fin)
                .maxUtilisations(max).nombreUtilisations(nb).event(event).build();
    }

    private Promotion PromoValide10pct() {
        return OnePromo(TypeReduction.PERCENTAGE, "10", today.minusDays(1), today.plusDays(10), 0, 100);
    }

    // quantite deja prise (stock) et deja prise par ce client (quota) pour une categorie
    private void stockEtQuota(CategorieBillet c, long pris, long dejaPrisParClient) {
        when(ligneReservationRepository.sumQuantiteActive(eq(c.getId()), any(LocalDateTime.class)))
                .thenReturn(pris);
        when(ligneReservationRepository.sumQuantiteActiveByClient(eq(c.getId()), eq(clientId), any(LocalDateTime.class)))
                .thenReturn(dejaPrisParClient);
    }

    private Reservation OneReservation(StatutReservation statut, LocalDateTime expiration, Promotion promotion, Event ev) {
        return Reservation.builder().reference("REF-TEST").statut(statut).dateExpiration(expiration)
                .client(client).event(ev).promotion(promotion).build();
    }

    // recupere la reservation envoyee a save()
    private Reservation reservationSauvegardee() {
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        return captor.getValue();
    }

    // =====================================================================
    // create : cas nominaux
    // =====================================================================
    @Test
    void create_nominalSansPromo_calculeLeTotalEtInitialiseLaReservation() {
        // ARRANGE
        ReservationRequest request = OneRequest(null, OneLigne(vipId, 1), OneLigne(stdId, 1));
        LocalDateTime avant = LocalDateTime.now();

        // ACT
        ReservationResponse result = service.create(eventId, request);

        // ASSERT
        LocalDateTime apres = LocalDateTime.now();
        assertSame(response, result);
        Reservation r = reservationSauvegardee();
        assertEquals(0, r.getMontantTotal().compareTo(new BigDecimal("650"))); // 500 + 150
        assertEquals(StatutReservation.PENDING_PAYMENT, r.getStatut());
        assertTrue(r.getReference().startsWith("REF-"));
        assertSame(client, r.getClient());
        assertSame(event, r.getEvent());
        assertNull(r.getPromotion());
        assertEquals(2, r.getLignes().size());
        // expiration = maintenant + 15 minutes (entre "avant" et "apres")
        assertFalse(r.getDateExpiration().isBefore(avant.plusMinutes(15)));
        assertFalse(r.getDateExpiration().isAfter(apres.plusMinutes(15)));
        verify(promotionRepository, never()).findByEventIdAndCode(any(), anyString());
        verify(promotionRepository, never()).incrementUtulisation(any());
    }

    @Test
    void create_chaqueLigne_garde_prixUnitaire_quantite_et_sousTotal() {
        service.create(eventId, OneRequest(null, OneLigne(vipId, 2)));

        Reservation r = reservationSauvegardee();
        assertEquals(1, r.getLignes().size());
        assertEquals(2, r.getLignes().get(0).getQuantite());
        assertEquals(0, r.getLignes().get(0).getPrixUnitaire().compareTo(new BigDecimal("500")));
        assertEquals(0, r.getLignes().get(0).getSousTotal().compareTo(new BigDecimal("1000")));
    }

    @Test
    void create_avecPromoPourcentage10_appliqueLaReductionEtIncrementeLUtilisation() {
        // ARRANGE
        Promotion promotion = PromoValide10pct();
        when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(promotion));
        when(promotionRepository.incrementUtulisation(promoId)).thenReturn(1);

        // ACT
        service.create(eventId, OneRequest("TEST10", OneLigne(vipId, 1), OneLigne(stdId, 1)));

        // ASSERT
        Reservation r = reservationSauvegardee();
        assertEquals(0, r.getMontantTotal().compareTo(new BigDecimal("585"))); // 650 - 10%
        assertSame(promotion, r.getPromotion());
        verify(promotionRepository).incrementUtulisation(promoId);
    }

    @Test
    void create_avecPromoMontantFixe50_soustraitLeMontant() {
        Promotion promotion = OnePromo(TypeReduction.FIXED_AMOUNT, "50", today.minusDays(1), today.plusDays(10), 0, 100);
        when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(promotion));
        when(promotionRepository.incrementUtulisation(promoId)).thenReturn(1);

        service.create(eventId, OneRequest("TEST10", OneLigne(vipId, 1), OneLigne(stdId, 1)));

        assertEquals(0, reservationSauvegardee().getMontantTotal().compareTo(new BigDecimal("600")));
    }

    @Test
    void create_promoMontantFixeSuperieurAuTotal_plafonneLeTotalAZero() {
        Promotion promotion = OnePromo(TypeReduction.FIXED_AMOUNT, "1000", today.minusDays(1), today.plusDays(10), 0, 100);
        when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(promotion));
        when(promotionRepository.incrementUtulisation(promoId)).thenReturn(1);

        service.create(eventId, OneRequest("TEST10", OneLigne(vipId, 1), OneLigne(stdId, 1)));

        assertEquals(0, reservationSauvegardee().getMontantTotal().compareTo(BigDecimal.ZERO));
    }

    @Test
    void create_stockExactementEgalALaQuantiteDemandee_estAccepte() {
        stockEtQuota(vip, 8, 0); // 10 - 8 = 2 disponibles

        service.create(eventId, OneRequest(null, OneLigne(vipId, 2)));

        assertEquals(0, reservationSauvegardee().getMontantTotal().compareTo(new BigDecimal("1000")));
    }

    @Test
    void create_quotaExactementAtteint_estAccepte() {
        stockEtQuota(vip, 0, 2); // 2 + 2 = 4 = quota

        service.create(eventId, OneRequest(null, OneLigne(vipId, 2)));

        assertEquals(1, reservationSauvegardee().getLignes().size());
    }

    @Test
    void create_referenceDejaExistante_enRegenereUneAutre() {
        // la 1ere reference existe deja (true), la 2eme est libre (false)
        when(reservationRepository.existsByReference(anyString())).thenReturn(true, false);

        service.create(eventId, OneRequest(null, OneLigne(stdId, 1)));

        verify(reservationRepository, times(2)).existsByReference(anyString());
    }

    // =====================================================================
    // create : erreurs
    // =====================================================================
    @Test
    void create_clientIntrouvable_ResourceNotFound_sansChercherLEvent() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 1))));

        assertTrue(ex.getMessage().contains(clientId.toString()));
        verify(eventRepository, never()).findById(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 1))));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_eventDraft_estInvisible_404() {
        createEventNonPublie(StatusEvenment.DRAFT);
    }

    @Test
    void create_eventCancelled_estInvisible_404() {
        createEventNonPublie(StatusEvenment.CANCELLED);
    }

    @Test
    void create_eventCompleted_estInvisible_404() {
        createEventNonPublie(StatusEvenment.COMPLETED);
    }

    @Test
    void create_ventePasEncoreOuverte_BusinessRule() {
        event.setDateOuvertureVente(now.plusDays(1));

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 1))));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_venteCloturee_BusinessRule() {
        event.setDateClotureVente(now.minusMinutes(1));

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 1))));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_memeCategorieDeuxFois_BusinessRule_avantTouteChargeEnBase() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 1), OneLigne(vipId, 2))));

        verify(categorieBilletRepository, never()).findAllByIdForUpdate(anyList());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_categorieIntrouvable_ResourceNotFound() {
        // la base ne retourne que vip : std est manquante
        when(categorieBilletRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(vip));

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 1), OneLigne(stdId, 1))));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_categorieDUnAutreEvent_BusinessRule() {
        Event autre = Event.builder().id(UUID.randomUUID()).statut(StatusEvenment.PUBLISHED).build();
        vip.setEvent(autre);

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 1))));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_stockInsuffisant_BusinessRule() {
        stockEtQuota(vip, 9, 0); // 1 disponible, on en veut 2

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 2))));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_stockEpuiseSurLaDeuxiemeLigne_refuseToutEtNeSauvegardeRien() {
        stockEtQuota(std, 100, 0); // plus rien en Standard

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 1), OneLigne(stdId, 1))));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_quotaDepasse_BusinessRule() {
        stockEtQuota(vip, 0, 3); // 3 + 2 = 5 > 4

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 2))));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_codePromoInexistant_BusinessRule_etNeSauvegardeRien() {
        when(promotionRepository.findByEventIdAndCode(eventId, "NEXISTEPAS")).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest("NEXISTEPAS", OneLigne(vipId, 1))));

        verify(promotionRepository, never()).incrementUtulisation(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_promoExpiree_BusinessRule_sansIncrementer() {
        Promotion expiree = OnePromo(TypeReduction.PERCENTAGE, "10", today.minusDays(10), today.minusDays(1), 0, 100);
        when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(expiree));

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest("TEST10", OneLigne(vipId, 1))));

        verify(promotionRepository, never()).incrementUtulisation(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_promoEpuisee_BusinessRule_sansIncrementer() {
        Promotion epuisee = OnePromo(TypeReduction.PERCENTAGE, "10", today.minusDays(1), today.plusDays(10), 2, 2);
        when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(epuisee));

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest("TEST10", OneLigne(vipId, 1))));

        verify(promotionRepository, never()).incrementUtulisation(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_promoPriseParUnAutreEntreTemps_incrementRetourneZero_BusinessRule() {
        when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(PromoValide10pct()));
        when(promotionRepository.incrementUtulisation(promoId)).thenReturn(0);

        assertThrows(BusinessRuleException.class,
                () -> service.create(eventId, OneRequest("TEST10", OneLigne(vipId, 1))));

        verify(reservationRepository, never()).save(any());
    }

    // =====================================================================
    // getById
    // =====================================================================
    @Test
    void getById_introuvable_ResourceNotFound() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.getById(reservationId, clientId));

        assertTrue(ex.getMessage().contains(reservationId.toString()));
    }

    @Test
    void getById_pasLeProprietaire_Forbidden() {
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, event);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

        assertThrows(ForbiddenOperationException.class, () -> service.getById(reservationId, UUID.randomUUID()));

        verify(reservationMapper, never()).toResponse(any(Reservation.class));
    }

    @Test
    void getById_proprietaire_retourneLaReponse() {
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, event);
        ReservationResponse resp = mock(ReservationResponse.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(reservationMapper.toResponse(r)).thenReturn(resp);

        assertSame(resp, service.getById(reservationId, clientId));
    }

    // =====================================================================
    // listByClient
    // =====================================================================
    @Test
    void listByClient_clientIntrouvable_ResourceNotFound() {
        Pageable pageable = PageRequest.of(0, 10);
        when(clientRepository.existsById(clientId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.listByClient(clientId, pageable));

        verify(reservationRepository, never()).findByClientId(any(), any());
    }

    @Test
    void listByClient_nominal_retourneLHistoriqueMappe() {
        // ARRANGE
        Pageable pageable = PageRequest.of(0, 10);
        Reservation r1 = OneReservation(StatutReservation.CONFIRMED, now.plusMinutes(5), null, event);
        Reservation r2 = OneReservation(StatutReservation.CANCELLED, now.plusMinutes(5), null, event);
        ReservationResponse resp1 = mock(ReservationResponse.class);
        ReservationResponse resp2 = mock(ReservationResponse.class);
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(reservationRepository.findByClientId(clientId, pageable))
                .thenReturn(new PageImpl<>(List.of(r1, r2), pageable, 2));
        when(reservationMapper.toResponse(r1)).thenReturn(resp1);
        when(reservationMapper.toResponse(r2)).thenReturn(resp2);

        // ACT
        Page<ReservationResponse> page = service.listByClient(clientId, pageable);

        // ASSERT
        assertEquals(2, page.getContent().size());
        assertSame(resp1, page.getContent().get(0));
        assertSame(resp2, page.getContent().get(1));
    }

    // =====================================================================
    // annuler
    // =====================================================================
    @Test
    void annuler_introuvable_ResourceNotFound() {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.annuler(reservationId, clientId));
    }

    @Test
    void annuler_pasLeProprietaire_Forbidden_etNeChangeRien() {
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, event);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertThrows(ForbiddenOperationException.class, () -> service.annuler(reservationId, UUID.randomUUID()));

        assertEquals(StatutReservation.PENDING_PAYMENT, r.getStatut());
    }

    @Test
    void annuler_statutConfirmed_BusinessRule() {
        annulerRefuse(StatutReservation.CONFIRMED);
    }

    @Test
    void annuler_statutCancelled_BusinessRule() {
        annulerRefuse(StatutReservation.CANCELLED);
    }

    @Test
    void annuler_statutExpired_BusinessRule() {
        annulerRefuse(StatutReservation.EXPIRED);
    }

    @Test
    void annuler_statutRefunded_BusinessRule() {
        annulerRefuse(StatutReservation.REFUNDED);
    }

    @Test
    void annuler_nominalSansPromo_passeEnCancelled_sansToucherAuxPromos() {
        // ARRANGE
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, event);
        ReservationResponse resp = mock(ReservationResponse.class);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));
        when(reservationMapper.toResponse(r)).thenReturn(resp);

        // ACT
        ReservationResponse result = service.annuler(reservationId, clientId);

        // ASSERT
        assertSame(resp, result);
        assertEquals(StatutReservation.CANCELLED, r.getStatut());
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    @Test
    void annuler_nominalAvecPromo_passeEnCancelled_etRendLUtilisation() {
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), PromoValide10pct(), event);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));
        when(reservationMapper.toResponse(r)).thenReturn(mock(ReservationResponse.class));

        service.annuler(reservationId, clientId);

        assertEquals(StatutReservation.CANCELLED, r.getStatut());
        verify(promotionRepository).devrementUtilisation(promoId);
    }

    // =====================================================================
    // expirer
    // =====================================================================
    @Test
    void expirer_introuvable_retourneFalse() {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

        assertFalse(service.expirer(reservationId));
    }

    @Test
    void expirer_statutConfirmed_retourneFalse_etNeChangeRien() {
        expirerRefuse(StatutReservation.CONFIRMED);
    }

    @Test
    void expirer_statutCancelled_retourneFalse_etNeChangeRien() {
        expirerRefuse(StatutReservation.CANCELLED);
    }

    @Test
    void expirer_statutExpired_retourneFalse_etNeChangeRien() {
        expirerRefuse(StatutReservation.EXPIRED);
    }

    @Test
    void expirer_statutRefunded_retourneFalse_etNeChangeRien() {
        expirerRefuse(StatutReservation.REFUNDED);
    }

    @Test
    void expirer_pasEncoreExpiree_retourneFalse() {
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(5), PromoValide10pct(), event);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertFalse(service.expirer(reservationId));

        assertEquals(StatutReservation.PENDING_PAYMENT, r.getStatut());
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    @Test
    void expirer_expireeSansPromo_passeEnExpired() {
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.minusMinutes(1), null, event);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertTrue(service.expirer(reservationId));

        assertEquals(StatutReservation.EXPIRED, r.getStatut());
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    @Test
    void expirer_expireeAvecPromo_passeEnExpired_etRendLUtilisation() {
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.minusMinutes(1), PromoValide10pct(), event);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertTrue(service.expirer(reservationId));

        assertEquals(StatutReservation.EXPIRED, r.getStatut());
        verify(promotionRepository).devrementUtilisation(promoId);
    }

    // =====================================================================
    // annulerPourEvenementAnnule
    // =====================================================================
    @Test
    void annulerPourEvenementAnnule_introuvable_retourneFalse() {
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

        assertFalse(service.annulerPourEvenementAnnule(reservationId));
    }

    @Test
    void annulerPourEvenementAnnule_statutConfirmed_retourneFalse() {
        annulerPourEvenementRefuseStatut(StatutReservation.CONFIRMED);
    }

    @Test
    void annulerPourEvenementAnnule_statutCancelled_retourneFalse() {
        annulerPourEvenementRefuseStatut(StatutReservation.CANCELLED);
    }

    @Test
    void annulerPourEvenementAnnule_statutExpired_retourneFalse() {
        annulerPourEvenementRefuseStatut(StatutReservation.EXPIRED);
    }

    @Test
    void annulerPourEvenementAnnule_statutRefunded_retourneFalse() {
        annulerPourEvenementRefuseStatut(StatutReservation.REFUNDED);
    }

    @Test
    void annulerPourEvenementAnnule_eventDraft_retourneFalse() {
        annulerPourEvenementRefuseEvent(StatusEvenment.DRAFT);
    }

    @Test
    void annulerPourEvenementAnnule_eventPublished_retourneFalse() {
        annulerPourEvenementRefuseEvent(StatusEvenment.PUBLISHED);
    }

    @Test
    void annulerPourEvenementAnnule_eventCompleted_retourneFalse() {
        annulerPourEvenementRefuseEvent(StatusEvenment.COMPLETED);
    }

    @Test
    void annulerPourEvenementAnnule_nominalSansPromo_passeEnCancelled() {
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, EventAnnule());
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertTrue(service.annulerPourEvenementAnnule(reservationId));

        assertEquals(StatutReservation.CANCELLED, r.getStatut());
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    @Test
    void annulerPourEvenementAnnule_nominalAvecPromo_passeEnCancelled_etRendLUtilisation() {
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), PromoValide10pct(), EventAnnule());
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertTrue(service.annulerPourEvenementAnnule(reservationId));

        assertEquals(StatutReservation.CANCELLED, r.getStatut());
        verify(promotionRepository).devrementUtilisation(promoId);
    }

    // =====================================================================
    // methodes utilitaires (evitent de repeter le meme code dans plusieurs tests)
    // =====================================================================
    private Event EventAnnule() {
        return Event.builder().id(eventId).statut(StatusEvenment.CANCELLED).build();
    }

    private void createEventNonPublie(StatusEvenment statut) {
        event.setStatut(statut);

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(eventId, OneRequest(null, OneLigne(vipId, 1))));

        verify(reservationRepository, never()).save(any());
    }

    private void annulerRefuse(StatutReservation statut) {
        Reservation r = OneReservation(statut, now.plusMinutes(10), PromoValide10pct(), event);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertThrows(BusinessRuleException.class, () -> service.annuler(reservationId, clientId));

        assertEquals(statut, r.getStatut()); // statut inchange
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    private void expirerRefuse(StatutReservation statut) {
        Reservation r = OneReservation(statut, now.minusMinutes(5), PromoValide10pct(), event);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertFalse(service.expirer(reservationId));

        assertEquals(statut, r.getStatut());
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    private void annulerPourEvenementRefuseStatut(StatutReservation statut) {
        Reservation r = OneReservation(statut, now.plusMinutes(10), PromoValide10pct(), EventAnnule());
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertFalse(service.annulerPourEvenementAnnule(reservationId));

        assertEquals(statut, r.getStatut());
        verify(promotionRepository, never()).devrementUtilisation(any());
    }

    private void annulerPourEvenementRefuseEvent(StatusEvenment statutEvent) {
        Event ev = Event.builder().id(eventId).statut(statutEvent).build();
        Reservation r = OneReservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, ev);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

        assertFalse(service.annulerPourEvenementAnnule(reservationId));

        assertEquals(StatutReservation.PENDING_PAYMENT, r.getStatut());
    }
}