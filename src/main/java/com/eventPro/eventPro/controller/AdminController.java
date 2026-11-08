package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.user.UserResponse;
import com.eventPro.eventPro.dto.remboursement.RemboursementResponse;
import com.eventPro.eventPro.dto.reservation.ReservationResponse;
import com.eventPro.eventPro.model.enums.RoleUser;
import com.eventPro.eventPro.model.enums.StatutRemboursement;
import com.eventPro.eventPro.model.enums.StatutReservation;
import com.eventPro.eventPro.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // TEMPORAIRE : adminId sera remplacé par l'utilisateur connecté
    @GetMapping("/users")
    public PagedModel<UserResponse> listUsers(
            @RequestParam UUID adminId,
            @RequestParam(required = false) RoleUser role,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return new PagedModel<>(adminService.listUsers(adminId, role, pageable));
    }

    @GetMapping("/reservations")
    public PagedModel<ReservationResponse> listReservations(
            @RequestParam UUID adminId,
            @RequestParam(required = false) StatutReservation statut,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) UUID clientId,
            @PageableDefault(size = 20, sort = "dateCreation", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(adminService.listReservations(adminId, statut, eventId, clientId, pageable));
    }

    @GetMapping("/remboursements")
    public PagedModel<RemboursementResponse> listRemboursements(
            @RequestParam UUID adminId,
            @RequestParam(defaultValue = "REJECTED") StatutRemboursement statut,
            @PageableDefault(size = 20, sort = "dateDemande", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(adminService.listRemboursements(adminId, statut, pageable));
    }

    @PatchMapping("/remboursements/{id}/approuver")
    public RemboursementResponse approuverRemboursement(@PathVariable UUID id, @RequestParam UUID adminId) {
        return adminService.approuverRemboursement(adminId, id);
    }
}