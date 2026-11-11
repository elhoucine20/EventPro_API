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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private AdministrateurRepository administrateurRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private RemboursementRepository remboursementRepository;
    @Mock private RemboursementService remboursementService;
    @Mock private UserMapper userMapper;
    @Mock private ReservationMapper reservationMapper;
    @Mock private RemboursementMapper remboursementMapper;

    @InjectMocks private AdminService service;

    private final UUID adminId = UUID.randomUUID();
    private final Pageable pageable = PageRequest.of(0, 20);

    // =====================================================================
    @Nested
    @DisplayName("admin inconnu : aucune methode ne touche aux donnees")
    class AdminInconnu {

        @Test
        void listUsers() {
            when(administrateurRepository.existsById(adminId)).thenReturn(false);

            assertThatThrownBy(() -> service.listUsers(adminId, null, pageable))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(adminId.toString());

            verifyNoInteractions(userRepository);
        }

        @Test
        void listReservations() {
            when(administrateurRepository.existsById(adminId)).thenReturn(false);

            assertThatThrownBy(() -> service.listReservations(adminId, null, null, null, pageable))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(reservationRepository);
        }

        @Test
        void listRemboursements() {
            when(administrateurRepository.existsById(adminId)).thenReturn(false);

            assertThatThrownBy(() -> service.listRemboursements(adminId, StatutRemboursement.REJECTED, pageable))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(remboursementRepository);
        }

        @Test
        void approuverRemboursement() {
            when(administrateurRepository.existsById(adminId)).thenReturn(false);

            assertThatThrownBy(() -> service.approuverRemboursement(adminId, UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(remboursementService);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listUsers")
    class ListUsers {

        @Test
        void sansRole_retourneTousLesUtilisateurs() {
            User u1 = mock(User.class);
            UserResponse r1 = mock(UserResponse.class);
            when(administrateurRepository.existsById(adminId)).thenReturn(true);
            when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(u1), pageable, 1));
            when(userMapper.toResponse(u1)).thenReturn(r1);

            Page<UserResponse> page = service.listUsers(adminId, null, pageable);

            assertThat(page.getContent()).containsExactly(r1);
            verify(userRepository, never()).findByRole(any(), any());
        }

        @Test
        void avecRole_filtreParRole() {
            User u1 = mock(User.class);
            UserResponse r1 = mock(UserResponse.class);
            when(administrateurRepository.existsById(adminId)).thenReturn(true);
            when(userRepository.findByRole(RoleUser.CLIENT, pageable)).thenReturn(new PageImpl<>(List.of(u1), pageable, 1));
            when(userMapper.toResponse(u1)).thenReturn(r1);

            Page<UserResponse> page = service.listUsers(adminId, RoleUser.CLIENT, pageable);

            assertThat(page.getContent()).containsExactly(r1);
            verify(userRepository, never()).findAll(any(Pageable.class));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listReservations")
    class ListReservations {

        @Test
        void avecFiltres_retourneLaPageMappee() {
            Reservation res = mock(Reservation.class);
            ReservationResponse resp = mock(ReservationResponse.class);
            when(administrateurRepository.existsById(adminId)).thenReturn(true);
            when(reservationRepository.findAll(ArgumentMatchers.<Specification<Reservation>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(res), pageable, 1));
            when(reservationMapper.toResponse(res)).thenReturn(resp);

            Page<ReservationResponse> page = service.listReservations(
                    adminId, StatutReservation.REFUNDED, UUID.randomUUID(), UUID.randomUUID(), pageable);

            assertThat(page.getContent()).containsExactly(resp);
        }

        @Test
        void sansAucunFiltre_fonctionneAussi() {
            when(administrateurRepository.existsById(adminId)).thenReturn(true);
            when(reservationRepository.findAll(ArgumentMatchers.<Specification<Reservation>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            assertThat(service.listReservations(adminId, null, null, null, pageable).getContent()).isEmpty();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listRemboursements / approuverRemboursement")
    class Remboursements {

        @Test
        void listRemboursements_filtreParStatut() {
            Remboursement r = mock(Remboursement.class);
            RemboursementResponse resp = mock(RemboursementResponse.class);
            when(administrateurRepository.existsById(adminId)).thenReturn(true);
            when(remboursementRepository.findByStatut(StatutRemboursement.REJECTED, pageable))
                    .thenReturn(new PageImpl<>(List.of(r), pageable, 1));
            when(remboursementMapper.toResponse(r)).thenReturn(resp);

            Page<RemboursementResponse> page = service.listRemboursements(adminId, StatutRemboursement.REJECTED, pageable);

            assertThat(page.getContent()).containsExactly(resp);
        }

        @Test
        void approuverRemboursement_delegueAuServiceDeRemboursement() {
            UUID remboursementId = UUID.randomUUID();
            RemboursementResponse resp = mock(RemboursementResponse.class);
            when(administrateurRepository.existsById(adminId)).thenReturn(true);
            when(remboursementService.approuverExceptionnellement(remboursementId)).thenReturn(resp);

            assertThat(service.approuverRemboursement(adminId, remboursementId)).isSameAs(resp);
        }

        @Test
        void approuverRemboursement_propageLErreurMetier() {
            UUID remboursementId = UUID.randomUUID();
            when(administrateurRepository.existsById(adminId)).thenReturn(true);
            when(remboursementService.approuverExceptionnellement(remboursementId))
                    .thenThrow(new BusinessRuleException("deja approuve"));

            assertThatThrownBy(() -> service.approuverRemboursement(adminId, remboursementId))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}