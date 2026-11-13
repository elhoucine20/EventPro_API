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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT)
class StatsServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganisateurRepository organisateurRepository;
    @Mock
    private CategorieBilletRepository categorieBilletRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private LigneReservationRepository ligneReservationRepository;
    @Mock
    private RemboursementRepository remboursementRepository;

    @InjectMocks
    private StatsService service;

    private final UUID orgaId = UUID.randomUUID();
    private final UUID eventId1 = UUID.randomUUID();
    private final UUID eventId2 = UUID.randomUUID();

    // ---------- petits helpers (comme OneUser() avant) ----------
    private Organisateur OneOrganisateur() {
        return Organisateur.builder().id(orgaId).nomOrganisation("PayEvents").build();
    }

    private Event OneEvent(UUID id, String titre) {
        return Event.builder().id(id).titre(titre).statut(StatusEvenment.PUBLISHED).organisateur(OneOrganisateur()).build();
    }

    // ligne "cle / quantite / montant" (ventes)
    private CleQuantiteMontant OneVente(UUID cle, long quantite, String montant) {
        CleQuantiteMontant c = mock(CleQuantiteMontant.class);
        when(c.cle()).thenReturn(cle);
        when(c.quantite()).thenReturn(quantite);
        when(c.montant()).thenReturn(new BigDecimal(montant));
        return c;
    }

    // ligne "cle / quantite" (billets vendus, capacites)
    private CleQuantite OneQuantite(UUID cle, long quantite) {
        CleQuantite c = mock(CleQuantite.class);
        when(c.cle()).thenReturn(cle);
        when(c.quantite()).thenReturn(quantite);
        return c;
    }

    // ligne "cle / montant" (rembourse)
    private CleMontant OneMontant(UUID cle, String montant) {
        CleMontant c = mock(CleMontant.class);
        when(c.cle()).thenReturn(cle);
        when(c.montant()).thenReturn(new BigDecimal(montant));
        return c;
    }

    // branche les 4 requetes d'agregation pour ces ids
    private void stubAgregats(List<UUID> ids, List<CleQuantiteMontant> ventes, List<CleQuantite> billets,
                              List<CleQuantite> capacites, List<CleMontant> rembourses) {
        when(reservationRepository.statsVentesParEvent(ids)).thenReturn(ventes);
        when(ligneReservationRepository.sumBilletsVendusParEvent(ids)).thenReturn(billets);
        when(categorieBilletRepository.sumQuantiteTotaleParEvent(ids)).thenReturn(capacites);
        when(remboursementRepository.sumMontantApprouveParEvent(ids)).thenReturn(rembourses);
    }

    // =====================================================================
    // statsOrganisateur
    // =====================================================================
    @Test
    void statsOrganisateur_organisateurIntrouvable_ResourceNotFound() {
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.statsOrganisateur(orgaId));

        assertTrue(ex.getMessage().contains(orgaId.toString()));
        verifyNoInteractions(eventRepository);
    }

    @Test
    void statsOrganisateur_sansAucunEvent_toutEstAZero_etAucuneAgregationLancee() {
        // ARRANGE
        Organisateur orga = OneOrganisateur();
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
        when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of());

        // ACT
        OrganisateurStatsResponse result = service.statsOrganisateur(orgaId);

        // ASSERT
        assertEquals(orgaId, result.organisateurId());
        assertEquals("PayEvents", result.nomOrganisation());
        assertEquals(0, result.totalBilletsVendus());
        assertEquals(0, result.totalChiffreAffaires().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.totalRembourse().compareTo(BigDecimal.ZERO));
        assertTrue(result.events().isEmpty());
        verifyNoInteractions(reservationRepository, ligneReservationRepository,
                categorieBilletRepository, remboursementRepository);
    }

    @Test
    void statsOrganisateur_unEvent_calculeVentesBilletsCapaciteRembourseEtTaux() {
        // ARRANGE
        Organisateur orga = OneOrganisateur();
        Event e1 = OneEvent(eventId1, "Event A");
        List<CleQuantiteMontant> ventes = List.of(OneVente(eventId1, 4, "2600.00"));
        List<CleQuantite> billets = List.of(OneQuantite(eventId1, 8));
        List<CleQuantite> capacites = List.of(OneQuantite(eventId1, 112));
        List<CleMontant> rembourses = List.of(OneMontant(eventId1, "650.00"));
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
        when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of(e1));
        stubAgregats(List.of(eventId1), ventes, billets, capacites, rembourses);

        // ACT
        OrganisateurStatsResponse result = service.statsOrganisateur(orgaId);

        // ASSERT
        assertEquals(1, result.events().size());
        EventStatsResponse s = result.events().get(0);
        assertEquals(eventId1, s.eventId());
        assertEquals("Event A", s.titre());
        assertEquals(StatusEvenment.PUBLISHED, s.statut());
        assertEquals(4, s.reservationsConfirmees());
        assertEquals(8, s.billetsVendus());
        assertEquals(112, s.capaciteTotale());
        assertEquals(0, s.tauxRemplissage().compareTo(new BigDecimal("7.1")));   // 8 * 100 / 112 = 7.14 -> 7.1
        assertEquals(0, s.chiffreAffaires().compareTo(new BigDecimal("2600.00")));
        assertEquals(0, s.montantRembourse().compareTo(new BigDecimal("650.00")));
        assertTrue(s.categories().isEmpty());   // le detail par categorie n'existe que dans statsEvent
        assertEquals(8, result.totalBilletsVendus());
        assertEquals(0, result.totalChiffreAffaires().compareTo(new BigDecimal("2600.00")));
        assertEquals(0, result.totalRembourse().compareTo(new BigDecimal("650.00")));
    }

    @Test
    void statsOrganisateur_deuxEvents_totauxSommes_etEventSansDonneesAZero() {
        // ARRANGE : e2 n'a aucune vente, aucune capacite, aucun remboursement
        Organisateur orga = OneOrganisateur();
        Event e1 = OneEvent(eventId1, "Event A");
        Event e2 = OneEvent(eventId2, "Event B");
        List<CleQuantiteMontant> ventes = List.of(OneVente(eventId1, 4, "2600.00"));
        List<CleQuantite> billets = List.of(OneQuantite(eventId1, 8));
        List<CleQuantite> capacites = List.of(OneQuantite(eventId1, 100));
        List<CleMontant> rembourses = List.of(OneMontant(eventId1, "150.00"));
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
        when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of(e1, e2));
        stubAgregats(List.of(eventId1, eventId2), ventes, billets, capacites, rembourses);

        // ACT
        OrganisateurStatsResponse result = service.statsOrganisateur(orgaId);

        // ASSERT
        assertEquals(2, result.events().size());
        assertEquals(eventId1, result.events().get(0).eventId());
        assertEquals(eventId2, result.events().get(1).eventId());
        EventStatsResponse vide = result.events().get(1);
        assertEquals(0, vide.reservationsConfirmees());
        assertEquals(0, vide.billetsVendus());
        assertEquals(0, vide.capaciteTotale());
        assertEquals(0, vide.tauxRemplissage().compareTo(BigDecimal.ZERO)); // capacite 0 : pas de division par zero
        assertEquals(0, vide.chiffreAffaires().compareTo(BigDecimal.ZERO));
        assertEquals(0, vide.montantRembourse().compareTo(BigDecimal.ZERO));
        assertEquals(8, result.totalBilletsVendus());
        assertEquals(0, result.totalChiffreAffaires().compareTo(new BigDecimal("2600.00")));
        assertEquals(0, result.totalRembourse().compareTo(new BigDecimal("150.00")));
    }

    @Test
    void statsOrganisateur_tauxArrondi_2Sur3_donne66virgule7() {
        Organisateur orga = OneOrganisateur();
        Event e1 = OneEvent(eventId1, "Event A");
        List<CleQuantite> billets = List.of(OneQuantite(eventId1, 2));
        List<CleQuantite> capacites = List.of(OneQuantite(eventId1, 3));
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
        when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of(e1));
        stubAgregats(List.of(eventId1), List.of(), billets, capacites, List.of());

        OrganisateurStatsResponse result = service.statsOrganisateur(orgaId);

        assertEquals(0, result.events().get(0).tauxRemplissage().compareTo(new BigDecimal("66.7")));
    }

    @Test
    void statsOrganisateur_toutVendu_tauxDe100() {
        Organisateur orga = OneOrganisateur();
        Event e1 = OneEvent(eventId1, "Event A");
        List<CleQuantite> billets = List.of(OneQuantite(eventId1, 50));
        List<CleQuantite> capacites = List.of(OneQuantite(eventId1, 50));
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
        when(eventRepository.findAllByOrganisateurId(orgaId)).thenReturn(List.of(e1));
        stubAgregats(List.of(eventId1), List.of(), billets, capacites, List.of());

        OrganisateurStatsResponse result = service.statsOrganisateur(orgaId);

        assertEquals(0, result.events().get(0).tauxRemplissage().compareTo(new BigDecimal("100")));
    }

    // =====================================================================
    // statsEvent
    // =====================================================================
    @Test
    void statsEvent_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId1)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.statsEvent(eventId1, orgaId));

        assertTrue(ex.getMessage().contains(eventId1.toString()));
        verifyNoInteractions(reservationRepository, ligneReservationRepository);
    }

    @Test
    void statsEvent_pasLOrganisateurDeLEvent_Forbidden() {
        Event e1 = OneEvent(eventId1, "Event A");
        when(eventRepository.findById(eventId1)).thenReturn(Optional.of(e1));

        assertThrows(ForbiddenOperationException.class, () -> service.statsEvent(eventId1, UUID.randomUUID()));

        verifyNoInteractions(reservationRepository, ligneReservationRepository, categorieBilletRepository);
    }

    @Test
    void statsEvent_nominal_ajouteLeDetailParCategorie_etZeroPourUneCategorieSansVente() {
        // ARRANGE
        UUID vipId = UUID.randomUUID();
        UUID stdId = UUID.randomUUID();
        Event e1 = OneEvent(eventId1, "Event A");
        CategorieBillet vip = CategorieBillet.builder().id(vipId).nom("VIP").quantiteTotale(10L).build();
        CategorieBillet std = CategorieBillet.builder().id(stdId).nom("Standard").quantiteTotale(100L).build();
        List<CleQuantiteMontant> ventesEvent = List.of(OneVente(eventId1, 3, "1650.00"));
        List<CleQuantite> billets = List.of(OneQuantite(eventId1, 4));
        List<CleQuantite> capacites = List.of(OneQuantite(eventId1, 110));
        List<CleMontant> rembourses = List.of(OneMontant(eventId1, "0.00"));
        List<CleQuantiteMontant> ventesParCategorie = List.of(OneVente(vipId, 3, "1500.00")); // Standard : aucune vente
        when(eventRepository.findById(eventId1)).thenReturn(Optional.of(e1));
        stubAgregats(List.of(eventId1), ventesEvent, billets, capacites, rembourses);
        when(ligneReservationRepository.sumVentesParCategorie(eventId1)).thenReturn(ventesParCategorie);
        when(categorieBilletRepository.findByEventId(eventId1)).thenReturn(List.of(vip, std));

        // ACT
        EventStatsResponse result = service.statsEvent(eventId1, orgaId);

        // ASSERT
        assertEquals(eventId1, result.eventId());
        assertEquals("Event A", result.titre());
        assertEquals(3, result.reservationsConfirmees());
        assertEquals(4, result.billetsVendus());
        assertEquals(110, result.capaciteTotale());
        assertEquals(0, result.chiffreAffaires().compareTo(new BigDecimal("1650.00")));
        assertEquals(2, result.categories().size());
        assertEquals(new CategorieStats(vipId, "VIP", 10, 3L, new BigDecimal("1500.00")), result.categories().get(0));
        assertEquals(new CategorieStats(stdId, "Standard", 100, 0L, BigDecimal.ZERO), result.categories().get(1));
    }

    @Test
    void statsEvent_eventSansCategorie_categoriesVides() {
        Event e1 = OneEvent(eventId1, "Event A");
        when(eventRepository.findById(eventId1)).thenReturn(Optional.of(e1));
        stubAgregats(List.of(eventId1), List.of(), List.of(), List.of(), List.of());
        when(ligneReservationRepository.sumVentesParCategorie(eventId1)).thenReturn(List.of());
        when(categorieBilletRepository.findByEventId(eventId1)).thenReturn(List.of());

        EventStatsResponse result = service.statsEvent(eventId1, orgaId);

        assertTrue(result.categories().isEmpty());
        assertEquals(0, result.tauxRemplissage().compareTo(BigDecimal.ZERO));
        verify(ligneReservationRepository).sumVentesParCategorie(eventId1);
    }
}