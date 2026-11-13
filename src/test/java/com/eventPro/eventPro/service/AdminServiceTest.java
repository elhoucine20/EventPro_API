package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.remboursement.RemboursementResponse;
import com.eventPro.eventPro.dto.reservation.ReservationResponse;
import com.eventPro.eventPro.dto.user.UserResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.remboursement.RemboursementMapper;
import com.eventPro.eventPro.mapper.reservation.ReservationMapper;
import com.eventPro.eventPro.mapper.user.UserMapper;
import com.eventPro.eventPro.model.Remboursement;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.User;
import com.eventPro.eventPro.model.enums.RoleUser;
import com.eventPro.eventPro.model.enums.StatutRemboursement;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.repository.AdministrateurRepository;
import com.eventPro.eventPro.repository.RemboursementRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import com.eventPro.eventPro.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceTest {

    @Mock
    private AdministrateurRepository administrateurRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RemboursementRepository remboursementRepository;
    @Mock
    private RemboursementService remboursementService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private RemboursementMapper remboursementMapper;

    @InjectMocks
    private AdminService service;

    private final UUID adminId = UUID.randomUUID();
    private final Pageable pageable = PageRequest.of(0, 20);

    // =====================================================================
    // admin inconnu : aucune methode ne touche aux donnees
    // =====================================================================
    @Test
    void listUsers_adminInconnu_ResourceNotFound() {
        when(administrateurRepository.existsById(adminId)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.listUsers(adminId, null, pageable));

        assertTrue(ex.getMessage().contains(adminId.toString()));
        verifyNoInteractions(userRepository);
    }

    @Test
    void listReservations_adminInconnu_ResourceNotFound() {
        when(administrateurRepository.existsById(adminId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.listReservations(adminId, null, null, null, pageable));

        verifyNoInteractions(reservationRepository);
    }

    @Test
    void listRemboursements_adminInconnu_ResourceNotFound() {
        when(administrateurRepository.existsById(adminId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.listRemboursements(adminId, StatutRemboursement.REJECTED, pageable));

        verifyNoInteractions(remboursementRepository);
    }

    @Test
    void approuverRemboursement_adminInconnu_ResourceNotFound() {
        when(administrateurRepository.existsById(adminId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.approuverRemboursement(adminId, UUID.randomUUID()));

        verifyNoInteractions(remboursementService);
    }

    // =====================================================================
    // listUsers
    // =====================================================================
    @Test
    void listUsers_sansRole_retourneTousLesUtilisateurs() {
        // ARRANGE
        User u1 = mock(User.class);
        UserResponse r1 = mock(UserResponse.class);
        when(administrateurRepository.existsById(adminId)).thenReturn(true);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(u1), pageable, 1));
        when(userMapper.toResponse(u1)).thenReturn(r1);

        // ACT
        Page<UserResponse> page = service.listUsers(adminId, null, pageable);

        // ASSERT
        assertEquals(1, page.getContent().size());
        assertSame(r1, page.getContent().get(0));
        verify(userRepository, never()).findByRole(any(), any());
    }

    @Test
    void listUsers_avecRole_filtreParRole() {
        // ARRANGE
        User u1 = mock(User.class);
        UserResponse r1 = mock(UserResponse.class);
        when(administrateurRepository.existsById(adminId)).thenReturn(true);
        when(userRepository.findByRole(RoleUser.CLIENT, pageable)).thenReturn(new PageImpl<>(List.of(u1), pageable, 1));
        when(userMapper.toResponse(u1)).thenReturn(r1);

        // ACT
        Page<UserResponse> page = service.listUsers(adminId, RoleUser.CLIENT, pageable);

        // ASSERT
        assertEquals(1, page.getContent().size());
        assertSame(r1, page.getContent().get(0));
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    // =====================================================================
    // listReservations
    // =====================================================================
    @Test
    void listReservations_avecFiltres_retourneLaPageMappee() {
        // ARRANGE
        Reservation res = mock(Reservation.class);
        ReservationResponse resp = mock(ReservationResponse.class);
        when(administrateurRepository.existsById(adminId)).thenReturn(true);
        when(reservationRepository.findAll(ArgumentMatchers.<Specification<Reservation>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(res), pageable, 1));
        when(reservationMapper.toResponse(res)).thenReturn(resp);

        // ACT
        Page<ReservationResponse> page = service.listReservations(
                adminId, StatutReservation.REFUNDED, UUID.randomUUID(), UUID.randomUUID(), pageable);

        // ASSERT
        assertEquals(1, page.getContent().size());
        assertSame(resp, page.getContent().get(0));
    }

    @Test
    void listReservations_sansAucunFiltre_fonctionneAussi() {
        when(administrateurRepository.existsById(adminId)).thenReturn(true);
        when(reservationRepository.findAll(ArgumentMatchers.<Specification<Reservation>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertTrue(service.listReservations(adminId, null, null, null, pageable).getContent().isEmpty());
    }

    // =====================================================================
    // listRemboursements / approuverRemboursement
    // =====================================================================
    @Test
    void listRemboursements_filtreParStatut() {
        // ARRANGE
        Remboursement r = mock(Remboursement.class);
        RemboursementResponse resp = mock(RemboursementResponse.class);
        when(administrateurRepository.existsById(adminId)).thenReturn(true);
        when(remboursementRepository.findByStatut(StatutRemboursement.REJECTED, pageable))
                .thenReturn(new PageImpl<>(List.of(r), pageable, 1));
        when(remboursementMapper.toResponse(r)).thenReturn(resp);

        // ACT
        Page<RemboursementResponse> page = service.listRemboursements(adminId, StatutRemboursement.REJECTED, pageable);

        // ASSERT
        assertEquals(1, page.getContent().size());
        assertSame(resp, page.getContent().get(0));
    }

    @Test
    void approuverRemboursement_delegueAuServiceDeRemboursement() {
        UUID remboursementId = UUID.randomUUID();
        RemboursementResponse resp = mock(RemboursementResponse.class);
        when(administrateurRepository.existsById(adminId)).thenReturn(true);
        when(remboursementService.approuverExceptionnellement(remboursementId)).thenReturn(resp);

        assertSame(resp, service.approuverRemboursement(adminId, remboursementId));
    }

    @Test
    void approuverRemboursement_propageLErreurMetier() {
        UUID remboursementId = UUID.randomUUID();
        when(administrateurRepository.existsById(adminId)).thenReturn(true);
        when(remboursementService.approuverExceptionnellement(remboursementId))
                .thenThrow(new BusinessRuleException("deja approuve"));

        assertThrows(BusinessRuleException.class, () -> service.approuverRemboursement(adminId, remboursementId));
    }
}