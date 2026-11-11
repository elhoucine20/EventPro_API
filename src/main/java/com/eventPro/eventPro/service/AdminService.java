package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.remboursement.RemboursementResponse;
import com.eventPro.eventPro.dto.reservation.ReservationResponse;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.remboursement.RemboursementMapper;
import com.eventPro.eventPro.mapper.reservation.ReservationMapper;
import com.eventPro.eventPro.dto.user.UserResponse;
import com.eventPro.eventPro.mapper.user.UserMapper;
import com.eventPro.eventPro.model.Reservation;
import com.eventPro.eventPro.model.User;
import com.eventPro.eventPro.model.enums.RoleUser;
import com.eventPro.eventPro.model.enums.StatutRemboursement;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.repository.AdministrateurRepository;
import com.eventPro.eventPro.repository.RemboursementRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import com.eventPro.eventPro.repository.ReservationSpecs;
import com.eventPro.eventPro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdministrateurRepository administrateurRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final RemboursementRepository remboursementRepository;
    private final RemboursementService remboursementService;
    private final UserMapper userMapper;
    private final ReservationMapper reservationMapper;
    private final RemboursementMapper remboursementMapper;

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(UUID adminId, RoleUser role, Pageable pageable) {
        checkAdmin(adminId);
        Page<User> users = role == null
                ? userRepository.findAll(pageable)
                : userRepository.findByRole(role, pageable);
        return users.map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> listReservations(UUID adminId, StatutReservation statut,
                                                      UUID eventId, UUID clientId, Pageable pageable) {
        checkAdmin(adminId);
        Specification<Reservation> spec = Specification.allOf(
                ReservationSpecs.statutEgal(statut),
                ReservationSpecs.eventEgal(eventId),
                ReservationSpecs.clientEgal(clientId));
        return reservationRepository.findAll(spec, pageable).map(reservationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<RemboursementResponse> listRemboursements(UUID adminId, StatutRemboursement statut,
                                                          Pageable pageable) {
        checkAdmin(adminId);
        return remboursementRepository.findByStatut(statut, pageable).map(remboursementMapper::toResponse);
    }

    @Transactional
    public RemboursementResponse approuverRemboursement(UUID adminId, UUID remboursementId) {
        checkAdmin(adminId);
        return remboursementService.approuverExceptionnellement(remboursementId);
    }

    private void checkAdmin(UUID adminId) {
        if (!administrateurRepository.existsById(adminId)) {
            throw new ResourceNotFoundException("Administrateur introuvable : " + adminId);
        }
    }
}