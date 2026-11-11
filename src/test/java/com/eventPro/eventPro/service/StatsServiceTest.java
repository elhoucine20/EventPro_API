package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.stats.CategorieStats;
import com.eventPro.eventPro.dto.stats.EventStatsResponse;
import com.eventPro.eventPro.dto.stats.OrganisateurStatsResponse;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.model.CategorieBillet;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.Organisateur;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.repository.CategorieBilletRepository;
import com.eventPro.eventPro.repository.EventRepository;
import com.eventPro.eventPro.repository.LigneReservationRepository;
import com.eventPro.eventPro.repository.OrganisateurRepository;
import com.eventPro.eventPro.repository.RemboursementRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import com.eventPro.eventPro.repository.projection.CleMontant;
import com.eventPro.eventPro.repository.projection.CleQuantite;
import com.eventPro.eventPro.repository.projection.CleQuantiteMontant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private OrganisateurRepository organisateurRepository;
    @Mock private CategorieBilletRepository categorieBilletRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private LigneReservationRepository ligneReservationRepository;
    @Mock private RemboursementRepository remboursementRepository;

    @InjectMocks private StatsService service;

    private final UUID orgaId = UUID.randomUUID();
    private final UUID eventId1 = UUID.randomUUID();
    private final UUID eventId2 = UUID.randomUUID();

    // ================= helpers : TOUJOURS dans des variables locales, jamais dans when(...) =================

    private Organisateur organisateur() {
        return Organisateur.builder().id(orgaId).nomOrganisation("PayEvents").build();
    }

    private Event event(UUID id, String titre) {
        return Event.builder().id(id).titre(titre).statut(StatusEvenment.PUBLISHED).organisateur(organisateur()).build();
    }

    private CleQuantiteMontant cqm(UUID cle, long quantite, String montant) {
        CleQuantiteMontant c = mock(CleQuantiteMontant.class);
        lenient().when(c.cle()).thenReturn(cle);
        lenient().when(c.quantite()).thenReturn(quantite);
        lenient().when(c.montant()).thenReturn(new BigDecimal(montant));
        return c;
    }

    private CleQuantite cq(UUID cle, long quantite) {
        CleQuantite c = mock(CleQuantite.class);
        lenient().when(c.cle()).thenReturn(cle);
        lenient().when(c.quantite()).thenReturn(quantite);
        return c;
    }

    private CleMontant cm(UUID cle, String montant) {
        CleMontant c = mock(CleMontant.class);
        lenient().when(c.cle()).thenReturn(cle);
        lenient().when(c.montant()).thenReturn(new BigDecimal(montant));
        return c;
    }

    /** Branche les 4 requetes d'agregation de construireStats pour ces ids. */
    private void stubAgregats(List<UUID> ids, List<CleQuantiteMontant> ventes, List<CleQuantite> billets,
                              List<CleQuantite> capacites, List<CleMontant> rembourses) {
        when(reservationRepository.statsVentesParEvent(ids)).thenReturn(ventes);
        when(ligneReservationRepository.sumBilletsVendusParEvent(ids)).thenReturn(billets);
        when(categorieBilletRepository.sumQuantiteTotaleParEvent(ids)).thenReturn(capacites);
        when(remboursementRepository.sumMontantApprouveParEvent(ids)).thenReturn(rembourses);
    }

    // =====================================================================
    @Nested
    @DisplayName("statsOrganisateur")
    class StatsOrganisateur {

        @Test
        void organisateurIntrouvable_leveResourceNotFound() {
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.statsOrganisateur(orgaId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(orgaId.toString());

            verifyNoInteractions(eventRepository);
        }

        @Test
        void sansAucunEvent_toutEstAZero_etAucuneAgregationLancee() {
            Organisateur orga = organisateur();
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
            when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of());

            OrganisateurStatsResponse result = service.statsOrganisateur(orgaId);

            assertThat(result.organisateurId()).isEqualTo(orgaId);
            assertThat(result.nomOrganisation()).isEqualTo("PayEvents");
            assertThat(result.totalBilletsVendus()).isZero();
            assertThat(result.totalChiffreAffaires()).isEqualByComparingTo("0");
            assertThat(result.totalRembourse()).isEqualByComparingTo("0");
            assertThat(result.events()).isEmpty();
            verifyNoInteractions(reservationRepository, ligneReservationRepository,
                    categorieBilletRepository, remboursementRepository);
        }

        @Test
        void unEvent_calculeVentesBilletsCapaciteRembourseEtTaux() {
            Organisateur orga = organisateur();
            Event e1 = event(eventId1, "Event A");
            List<CleQuantiteMontant> ventes = List.of(cqm(eventId1, 4, "2600.00"));
            List<CleQuantite> billets = List.of(cq(eventId1, 8));
            List<CleQuantite> capacites = List.of(cq(eventId1, 112));
            List<CleMontant> rembourses = List.of(cm(eventId1, "650.00"));
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
            when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of(e1));
            stubAgregats(List.of(eventId1), ventes, billets, capacites, rembourses);

            OrganisateurStatsResponse result = service.statsOrganisateur(orgaId);

            assertThat(result.events()).hasSize(1);
            EventStatsResponse s = result.events().get(0);
            assertThat(s.eventId()).isEqualTo(eventId1);
            assertThat(s.titre()).isEqualTo("Event A");
            assertThat(s.statut()).isEqualTo(StatusEvenment.PUBLISHED);
            assertThat(s.reservationsConfirmees()).isEqualTo(4);
            assertThat(s.billetsVendus()).isEqualTo(8);
            assertThat(s.capaciteTotale()).isEqualTo(112);
            assertThat(s.tauxRemplissage()).isEqualByComparingTo("7.1");   // 8 * 100 / 112 = 7.14 -> 7.1
            assertThat(s.chiffreAffaires()).isEqualByComparingTo("2600.00");
            assertThat(s.montantRembourse()).isEqualByComparingTo("650.00");
            assertThat(s.categories()).isEmpty();   // le detail par categorie n'existe que dans statsEvent
            assertThat(result.totalBilletsVendus()).isEqualTo(8);
            assertThat(result.totalChiffreAffaires()).isEqualByComparingTo("2600.00");
            assertThat(result.totalRembourse()).isEqualByComparingTo("650.00");
        }

        @Test
        void deuxEvents_totauxSommes_etEventSansDonneesAZero() {
            Organisateur orga = organisateur();
            Event e1 = event(eventId1, "Event A");
            Event e2 = event(eventId2, "Event B");   // aucune vente, aucune capacite, aucun remboursement
            List<CleQuantiteMontant> ventes = List.of(cqm(eventId1, 4, "2600.00"));
            List<CleQuantite> billets = List.of(cq(eventId1, 8));
            List<CleQuantite> capacites = List.of(cq(eventId1, 100));
            List<CleMontant> rembourses = List.of(cm(eventId1, "150.00"));
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
            when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of(e1, e2));
            stubAgregats(List.of(eventId1, eventId2), ventes, billets, capacites, rembourses);

            OrganisateurStatsResponse result = service.statsOrganisateur(orgaId);

            assertThat(result.events()).extracting(EventStatsResponse::eventId).containsExactly(eventId1, eventId2);
            EventStatsResponse vide = result.events().get(1);
            assertThat(vide.reservationsConfirmees()).isZero();
            assertThat(vide.billetsVendus()).isZero();
            assertThat(vide.capaciteTotale()).isZero();
            assertThat(vide.tauxRemplissage()).isEqualByComparingTo("0");   // capacite 0 : pas de division par zero
            assertThat(vide.chiffreAffaires()).isEqualByComparingTo("0");
            assertThat(vide.montantRembourse()).isEqualByComparingTo("0");
            assertThat(result.totalBilletsVendus()).isEqualTo(8);
            assertThat(result.totalChiffreAffaires()).isEqualByComparingTo("2600.00");
            assertThat(result.totalRembourse()).isEqualByComparingTo("150.00");
        }

        @Test
        void tauxArrondiAuDixiemeSuperieur_2Sur3_donne66virgule7() {
            Organisateur orga = organisateur();
            Event e1 = event(eventId1, "Event A");
            List<CleQuantite> billets = List.of(cq(eventId1, 2));
            List<CleQuantite> capacites = List.of(cq(eventId1, 3));
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
            when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of(e1));
            stubAgregats(List.of(eventId1), List.of(), billets, capacites, List.of());

            OrganisateurStatsResponse result = service.statsOrganisateur(orgaId);

            assertThat(result.events().get(0).tauxRemplissage()).isEqualByComparingTo("66.7");
        }

        @Test
        void toutVendu_tauxDe100() {
            Organisateur orga = organisateur();
            Event e1 = event(eventId1, "Event A");
            List<CleQuantite> billets = List.of(cq(eventId1, 50));
            List<CleQuantite> capacites = List.of(cq(eventId1, 50));
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
            when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of(e1));
            stubAgregats(List.of(eventId1), List.of(), billets, capacites, List.of());

            assertThat(service.statsOrganisateur(orgaId).events().get(0).tauxRemplissage()).isEqualByComparingTo("100");
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("statsEvent")
    class StatsEvent {

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.statsEvent(eventId1, orgaId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(eventId1.toString());

            verifyNoInteractions(reservationRepository, ligneReservationRepository);
        }

        @Test
        void pasLOrganisateurDeLEvent_leveForbidden() {
            Event e1 = event(eventId1, "Event A");
            when(eventRepository.findById(eventId1)).thenReturn(Optional.of(e1));

            assertThatThrownBy(() -> service.statsEvent(eventId1, UUID.randomUUID()))
                    .isInstanceOf(ForbiddenOperationException.class);

            verifyNoInteractions(reservationRepository, ligneReservationRepository, categorieBilletRepository);
        }

        @Test
        void nominal_ajouteLeDetailParCategorie_etZeroPourUneCategorieSansVente() {
            UUID vipId = UUID.randomUUID();
            UUID stdId = UUID.randomUUID();
            Event e1 = event(eventId1, "Event A");
            CategorieBillet vip = CategorieBillet.builder().id(vipId).nom("VIP").quantiteTotale(10L).build();
            CategorieBillet std = CategorieBillet.builder().id(stdId).nom("Standard").quantiteTotale(100L).build();
            List<CleQuantiteMontant> ventesEvent = List.of(cqm(eventId1, 3, "1650.00"));
            List<CleQuantite> billets = List.of(cq(eventId1, 4));
            List<CleQuantite> capacites = List.of(cq(eventId1, 110));
            List<CleMontant> rembourses = List.of(cm(eventId1, "0.00"));
            List<CleQuantiteMontant> ventesParCategorie = List.of(cqm(vipId, 3, "1500.00"));   // Standard : aucune vente

            when(eventRepository.findById(eventId1)).thenReturn(Optional.of(e1));
            stubAgregats(List.of(eventId1), ventesEvent, billets, capacites, rembourses);
            when(ligneReservationRepository.sumVentesParCategorie(eventId1)).thenReturn(ventesParCategorie);
            when(categorieBilletRepository.findByEventId(eventId1)).thenReturn(List.of(vip, std));

            EventStatsResponse result = service.statsEvent(eventId1, orgaId);

            assertThat(result.eventId()).isEqualTo(eventId1);
            assertThat(result.titre()).isEqualTo("Event A");
            assertThat(result.reservationsConfirmees()).isEqualTo(3);
            assertThat(result.billetsVendus()).isEqualTo(4);
            assertThat(result.capaciteTotale()).isEqualTo(110);
            assertThat(result.chiffreAffaires()).isEqualByComparingTo("1650.00");
            assertThat(result.categories()).containsExactly(
                    new CategorieStats(vipId, "VIP", 10, 3L, new BigDecimal("1500.00")),
                    new CategorieStats(stdId, "Standard", 100, 0L, BigDecimal.ZERO));
        }

        @Test
        void eventSansCategorie_categoriesVides() {
            Event e1 = event(eventId1, "Event A");
            when(eventRepository.findById(eventId1)).thenReturn(Optional.of(e1));
            stubAgregats(List.of(eventId1), List.of(), List.of(), List.of(), List.of());
            when(ligneReservationRepository.sumVentesParCategorie(eventId1)).thenReturn(List.of());
            when(categorieBilletRepository.findByEventId(eventId1)).thenReturn(List.of());

            EventStatsResponse result = service.statsEvent(eventId1, orgaId);

            assertThat(result.categories()).isEmpty();
            assertThat(result.tauxRemplissage()).isEqualByComparingTo("0");
            verify(ligneReservationRepository).sumVentesParCategorie(eventId1);
        }
    }
}