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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private LigneReservationRepository ligneReservationRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private EventRepository eventRepository;
    @Mock private CategorieBilletRepository categorieBilletRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private ReservationMapper reservationMapper;

    @InjectMocks private ReservationService service;

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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "holdMinutes", 15L);

        client = mock(Client.class);
        lenient().when(client.getId()).thenReturn(clientId);

        event = Event.builder()
                .id(eventId)
                .statut(StatusEvenment.PUBLISHED)
                .dateOuvertureVente(now.minusDays(1))
                .dateClotureVente(now.plusDays(10))
                .build();

        vip = CategorieBillet.builder().id(vipId).nom("VIP")
                .prixUnitaire(new BigDecimal("500.00")).quantiteTotale(10L).quotaMaxParClient(4).event(event).build();
        std = CategorieBillet.builder().id(stdId).nom("Standard")
                .prixUnitaire(new BigDecimal("150.00")).quantiteTotale(100L).quotaMaxParClient(6).event(event).build();
    }

    // ================= helpers =================

    private LigneReservationRequest ligne(UUID categorieId, int quantite) {
        LigneReservationRequest l = mock(LigneReservationRequest.class);
        lenient().when(l.categorieBilletId()).thenReturn(categorieId);
        lenient().when(l.quantite()).thenReturn(quantite);
        return l;
    }

    private ReservationRequest request(String promoCode, LigneReservationRequest... lignes) {
        ReservationRequest r = mock(ReservationRequest.class);
        lenient().when(r.clientId()).thenReturn(clientId);
        lenient().when(r.promoCode()).thenReturn(promoCode);
        lenient().when(r.lignes()).thenReturn(List.of(lignes));
        return r;
    }

    private Promotion promo(TypeReduction type, String valeur, LocalDate debut, LocalDate fin, int nb, int max) {
        return Promotion.builder().id(promoId).code("TEST10").typeReduction(type)
                .valeurReduction(new BigDecimal(valeur)).dateDebut(debut).dateFin(fin)
                .maxUtilisations(max).nombreUtilisations(nb).event(event).build();
    }

    private Promotion promoValide10pct() {
        return promo(TypeReduction.PERCENTAGE, "10", today.minusDays(1), today.plusDays(10), 0, 100);
    }

    private void stockEtQuota(CategorieBillet c, long pris, long dejaPrisParClient) {
        lenient().when(ligneReservationRepository.sumQuantiteActive(eq(c.getId()), any(LocalDateTime.class)))
                .thenReturn(pris);
        lenient().when(ligneReservationRepository.sumQuantiteActiveByClient(eq(c.getId()), eq(clientId), any(LocalDateTime.class)))
                .thenReturn(dejaPrisParClient);
    }

    private Reservation reservation(StatutReservation statut, LocalDateTime expiration, Promotion promotion, Event ev) {
        return Reservation.builder()
                .reference("REF-TEST")
                .statut(statut)
                .dateExpiration(expiration)
                .client(client)
                .event(ev)
                .promotion(promotion)
                .build();
    }

    // =====================================================================
    @Nested
    @DisplayName("create")
    class Create {

        private ReservationResponse response;

        @BeforeEach
        void env() {
            response = mock(ReservationResponse.class);
            lenient().when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
            lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            lenient().when(categorieBilletRepository.findAllByIdForUpdate(anyList())).thenAnswer(inv -> {
                List<UUID> ids = inv.getArgument(0);
                return List.of(vip, std).stream().filter(c -> ids.contains(c.getId())).toList();
            });
            stockEtQuota(vip, 0, 0);
            stockEtQuota(std, 0, 0);
            lenient().when(reservationRepository.existsByReference(anyString())).thenReturn(false);
            lenient().when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(reservationMapper.toResponse(any(Reservation.class))).thenReturn(response);
        }

        private Reservation reservationSauvegardee() {
            ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationRepository).save(captor.capture());
            return captor.getValue();
        }

        @Test
        void nominalSansPromo_calculeLeTotalEtInitialiseLaReservation() {
            ReservationRequest request = request(null, ligne(vipId, 1), ligne(stdId, 1));
            LocalDateTime avant = LocalDateTime.now();

            ReservationResponse result = service.create(eventId, request);

            LocalDateTime apres = LocalDateTime.now();
            assertThat(result).isSameAs(response);
            Reservation r = reservationSauvegardee();
            assertThat(r.getMontantTotal()).isEqualByComparingTo("650");
            assertThat(r.getStatut()).isEqualTo(StatutReservation.PENDING_PAYMENT);
            assertThat(r.getReference()).startsWith("REF-");
            assertThat(r.getClient()).isSameAs(client);
            assertThat(r.getEvent()).isSameAs(event);
            assertThat(r.getPromotion()).isNull();
            assertThat(r.getLignes()).hasSize(2);
            assertThat(r.getDateExpiration()).isBetween(avant.plusMinutes(15), apres.plusMinutes(15));
            verify(promotionRepository, never()).findByEventIdAndCode(any(), anyString());
            verify(promotionRepository, never()).incrementUtulisation(any());
        }

        @Test
        void chaqueLigne_garde_prixUnitaire_quantite_et_sousTotal() {
            service.create(eventId, request(null, ligne(vipId, 2)));

            Reservation r = reservationSauvegardee();
            assertThat(r.getLignes()).hasSize(1);
            assertThat(r.getLignes().get(0).getQuantite()).isEqualTo(2);
            assertThat(r.getLignes().get(0).getPrixUnitaire()).isEqualByComparingTo("500");
            assertThat(r.getLignes().get(0).getSousTotal()).isEqualByComparingTo("1000");
        }

        @Test
        void avecPromoPourcentage10_appliqueLaReductionEtIncrementeLUtilisation() {
            Promotion promotion = promoValide10pct();
            when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(promotion));
            when(promotionRepository.incrementUtulisation(promoId)).thenReturn(1);

            service.create(eventId, request("TEST10", ligne(vipId, 1), ligne(stdId, 1)));

            Reservation r = reservationSauvegardee();
            assertThat(r.getMontantTotal()).isEqualByComparingTo("585");   // 650 - 10%
            assertThat(r.getPromotion()).isSameAs(promotion);
            verify(promotionRepository).incrementUtulisation(promoId);
        }

        @Test
        void avecPromoMontantFixe50_soustraitLeMontant() {
            when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(
                    promo(TypeReduction.FIXED_AMOUNT, "50", today.minusDays(1), today.plusDays(10), 0, 100)));
            when(promotionRepository.incrementUtulisation(promoId)).thenReturn(1);

            service.create(eventId, request("TEST10", ligne(vipId, 1), ligne(stdId, 1)));

            assertThat(reservationSauvegardee().getMontantTotal()).isEqualByComparingTo("600");
        }

        @Test
        void promoMontantFixeSuperieurAuTotal_plafonneLeTotalAZero() {
            when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(
                    promo(TypeReduction.FIXED_AMOUNT, "1000", today.minusDays(1), today.plusDays(10), 0, 100)));
            when(promotionRepository.incrementUtulisation(promoId)).thenReturn(1);

            service.create(eventId, request("TEST10", ligne(vipId, 1), ligne(stdId, 1)));

            assertThat(reservationSauvegardee().getMontantTotal()).isEqualByComparingTo("0");
        }

        @Test
        void stockExactementEgalALaQuantiteDemandee_estAccepte() {
            stockEtQuota(vip, 8, 0);   // 10 - 8 = 2 disponibles

            service.create(eventId, request(null, ligne(vipId, 2)));

            assertThat(reservationSauvegardee().getMontantTotal()).isEqualByComparingTo("1000");
        }

        @Test
        void quotaExactementAtteint_estAccepte() {
            stockEtQuota(vip, 0, 2);   // 2 + 2 = 4 = quota

            service.create(eventId, request(null, ligne(vipId, 2)));

            assertThat(reservationSauvegardee().getLignes()).hasSize(1);
        }

        @Test
        void referenceDejaExistante_enRegenereUneAutre() {
            when(reservationRepository.existsByReference(anyString())).thenReturn(true, false);

            service.create(eventId, request(null, ligne(stdId, 1)));

            verify(reservationRepository, times(2)).existsByReference(anyString());
        }

        // ---------- erreurs ----------

        @Test
        void clientIntrouvable_leveResourceNotFound_sansChercherLEvent() {
            when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 1))))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(clientId.toString());

            verify(eventRepository, never()).findById(any());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 1))))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(reservationRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, mode = EnumSource.Mode.EXCLUDE, names = "PUBLISHED")
        void eventNonPublie_estInvisible_404(StatusEvenment statut) {
            event.setStatut(statut);

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 1))))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(reservationRepository, never()).save(any());
        }

        @Test
        void ventePasEncoreOuverte_leveBusinessRule() {
            event.setDateOuvertureVente(now.plusDays(1));

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 1))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(reservationRepository, never()).save(any());
        }

        @Test
        void venteCloturee_leveBusinessRule() {
            event.setDateClotureVente(now.minusMinutes(1));

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 1))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(reservationRepository, never()).save(any());
        }

        @Test
        void memeCategorieDeuxFois_leveBusinessRule_avantTouteChargeEnBase() {
            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 1), ligne(vipId, 2))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(categorieBilletRepository, never()).findAllByIdForUpdate(anyList());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void categorieIntrouvable_leveResourceNotFound() {
            when(categorieBilletRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(vip)); // std manquante

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 1), ligne(stdId, 1))))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(reservationRepository, never()).save(any());
        }

        @Test
        void categorieDUnAutreEvent_leveBusinessRule() {
            Event autre = Event.builder().id(UUID.randomUUID()).statut(StatusEvenment.PUBLISHED).build();
            vip.setEvent(autre);

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 1))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(reservationRepository, never()).save(any());
        }

        @Test
        void stockInsuffisant_leveBusinessRule() {
            stockEtQuota(vip, 9, 0);   // 1 disponible, on en veut 2

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 2))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(reservationRepository, never()).save(any());
        }

        @Test
        void stockEpuiseSurLaDeuxiemeLigne_refuseToutEtNeSauvegardeRien() {
            stockEtQuota(std, 100, 0);   // plus rien en Standard

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 1), ligne(stdId, 1))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(reservationRepository, never()).save(any());
        }

        @Test
        void quotaDepasse_leveBusinessRule() {
            stockEtQuota(vip, 0, 3);   // 3 + 2 = 5 > 4

            assertThatThrownBy(() -> service.create(eventId, request(null, ligne(vipId, 2))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(reservationRepository, never()).save(any());
        }

        @Test
        void codePromoInexistant_leveBusinessRule_etNeSauvegardeRien() {
            when(promotionRepository.findByEventIdAndCode(eventId, "NEXISTEPAS")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(eventId, request("NEXISTEPAS", ligne(vipId, 1))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).incrementUtulisation(any());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void promoExpiree_leveBusinessRule_sansIncrementer() {
            when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(
                    promo(TypeReduction.PERCENTAGE, "10", today.minusDays(10), today.minusDays(1), 0, 100)));

            assertThatThrownBy(() -> service.create(eventId, request("TEST10", ligne(vipId, 1))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).incrementUtulisation(any());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void promoEpuisee_leveBusinessRule_sansIncrementer() {
            when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(
                    promo(TypeReduction.PERCENTAGE, "10", today.minusDays(1), today.plusDays(10), 2, 2)));

            assertThatThrownBy(() -> service.create(eventId, request("TEST10", ligne(vipId, 1))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).incrementUtulisation(any());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void promoPriseParUnAutreEntreTemps_incrementRetourneZero_leveBusinessRule() {
            when(promotionRepository.findByEventIdAndCode(eventId, "TEST10")).thenReturn(Optional.of(promoValide10pct()));
            when(promotionRepository.incrementUtulisation(promoId)).thenReturn(0);

            assertThatThrownBy(() -> service.create(eventId, request("TEST10", ligne(vipId, 1))))
                    .isInstanceOf(BusinessRuleException.class);

            verify(reservationRepository, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void introuvable_leveResourceNotFound() {
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(reservationId, clientId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(reservationId.toString());
        }

        @Test
        void pasLeProprietaire_leveForbidden() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, event);
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.getById(reservationId, UUID.randomUUID()))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(reservationMapper, never()).toResponse(any(Reservation.class));
        }

        @Test
        void proprietaire_retourneLaReponse() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, event);
            ReservationResponse response = mock(ReservationResponse.class);
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
            when(reservationMapper.toResponse(r)).thenReturn(response);

            assertThat(service.getById(reservationId, clientId)).isSameAs(response);
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
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(reservationRepository, never()).findByClientId(any(), any());
        }

        @Test
        void nominal_retourneLHistoriqueMappe() {
            Reservation r1 = reservation(StatutReservation.CONFIRMED, now.plusMinutes(5), null, event);
            Reservation r2 = reservation(StatutReservation.CANCELLED, now.plusMinutes(5), null, event);
            ReservationResponse resp1 = mock(ReservationResponse.class);
            ReservationResponse resp2 = mock(ReservationResponse.class);
            when(clientRepository.existsById(clientId)).thenReturn(true);
            when(reservationRepository.findByClientId(clientId, pageable))
                    .thenReturn(new PageImpl<>(List.of(r1, r2), pageable, 2));
            when(reservationMapper.toResponse(r1)).thenReturn(resp1);
            when(reservationMapper.toResponse(r2)).thenReturn(resp2);

            Page<ReservationResponse> page = service.listByClient(clientId, pageable);

            assertThat(page.getContent()).containsExactly(resp1, resp2);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("annuler")
    class Annuler {

        @Test
        void introuvable_leveResourceNotFound() {
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.annuler(reservationId, clientId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void pasLeProprietaire_leveForbidden_etNeChangeRien() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, event);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.annuler(reservationId, UUID.randomUUID()))
                    .isInstanceOf(ForbiddenOperationException.class);

            assertThat(r.getStatut()).isEqualTo(StatutReservation.PENDING_PAYMENT);
        }

        @ParameterizedTest
        @EnumSource(value = StatutReservation.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDING_PAYMENT")
        void statutDifferentDePending_leveBusinessRule(StatutReservation statut) {
            Reservation r = reservation(statut, now.plusMinutes(10), promoValide10pct(), event);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.annuler(reservationId, clientId))
                    .isInstanceOf(BusinessRuleException.class);

            assertThat(r.getStatut()).isEqualTo(statut);
            verify(promotionRepository, never()).devrementUtilisation(any());
        }

        @Test
        void nominalSansPromo_passeEnCancelled_sansToucherAuxPromos() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, event);
            ReservationResponse response = mock(ReservationResponse.class);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));
            when(reservationMapper.toResponse(r)).thenReturn(response);

            assertThat(service.annuler(reservationId, clientId)).isSameAs(response);

            assertThat(r.getStatut()).isEqualTo(StatutReservation.CANCELLED);
            verify(promotionRepository, never()).devrementUtilisation(any());
        }

        @Test
        void nominalAvecPromo_passeEnCancelled_etRendLUtilisation() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), promoValide10pct(), event);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));
            when(reservationMapper.toResponse(r)).thenReturn(mock(ReservationResponse.class));

            service.annuler(reservationId, clientId);

            assertThat(r.getStatut()).isEqualTo(StatutReservation.CANCELLED);
            verify(promotionRepository).devrementUtilisation(promoId);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("expirer")
    class Expirer {

        @Test
        void introuvable_retourneFalse() {
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

            assertThat(service.expirer(reservationId)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = StatutReservation.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDING_PAYMENT")
        void statutDifferentDePending_retourneFalse_etNeChangeRien(StatutReservation statut) {
            Reservation r = reservation(statut, now.minusMinutes(5), promoValide10pct(), event);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThat(service.expirer(reservationId)).isFalse();

            assertThat(r.getStatut()).isEqualTo(statut);
            verify(promotionRepository, never()).devrementUtilisation(any());
        }

        @Test
        void pasEncoreExpiree_retourneFalse() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(5), promoValide10pct(), event);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThat(service.expirer(reservationId)).isFalse();

            assertThat(r.getStatut()).isEqualTo(StatutReservation.PENDING_PAYMENT);
            verify(promotionRepository, never()).devrementUtilisation(any());
        }

        @Test
        void expireeSansPromo_passeEnExpired() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.minusMinutes(1), null, event);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThat(service.expirer(reservationId)).isTrue();

            assertThat(r.getStatut()).isEqualTo(StatutReservation.EXPIRED);
            verify(promotionRepository, never()).devrementUtilisation(any());
        }

        @Test
        void expireeAvecPromo_passeEnExpired_etRendLUtilisation() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.minusMinutes(1), promoValide10pct(), event);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThat(service.expirer(reservationId)).isTrue();

            assertThat(r.getStatut()).isEqualTo(StatutReservation.EXPIRED);
            verify(promotionRepository).devrementUtilisation(promoId);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("annulerPourEvenementAnnule")
    class AnnulerPourEvenementAnnule {

        private Event eventAnnule() {
            return Event.builder().id(eventId).statut(StatusEvenment.CANCELLED).build();
        }

        @Test
        void introuvable_retourneFalse() {
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

            assertThat(service.annulerPourEvenementAnnule(reservationId)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = StatutReservation.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDING_PAYMENT")
        void statutDifferentDePending_retourneFalse(StatutReservation statut) {
            Reservation r = reservation(statut, now.plusMinutes(10), promoValide10pct(), eventAnnule());
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThat(service.annulerPourEvenementAnnule(reservationId)).isFalse();

            assertThat(r.getStatut()).isEqualTo(statut);
            verify(promotionRepository, never()).devrementUtilisation(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, mode = EnumSource.Mode.EXCLUDE, names = "CANCELLED")
        void eventPasAnnule_retourneFalse(StatusEvenment statutEvent) {
            Event ev = Event.builder().id(eventId).statut(statutEvent).build();
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, ev);
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThat(service.annulerPourEvenementAnnule(reservationId)).isFalse();

            assertThat(r.getStatut()).isEqualTo(StatutReservation.PENDING_PAYMENT);
        }

        @Test
        void nominalSansPromo_passeEnCancelled() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), null, eventAnnule());
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThat(service.annulerPourEvenementAnnule(reservationId)).isTrue();

            assertThat(r.getStatut()).isEqualTo(StatutReservation.CANCELLED);
            verify(promotionRepository, never()).devrementUtilisation(any());
        }

        @Test
        void nominalAvecPromo_passeEnCancelled_etRendLUtilisation() {
            Reservation r = reservation(StatutReservation.PENDING_PAYMENT, now.plusMinutes(10), promoValide10pct(), eventAnnule());
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(r));

            assertThat(service.annulerPourEvenementAnnule(reservationId)).isTrue();

            assertThat(r.getStatut()).isEqualTo(StatutReservation.CANCELLED);
            verify(promotionRepository).devrementUtilisation(promoId);
        }
    }
}