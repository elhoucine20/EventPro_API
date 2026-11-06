package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.ticket.TicketResponse;
import com.eventPro.eventPro.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    // TEMPORAIRE : clientId sera remplacé par l'utilisateur connecté
    @GetMapping("/reservations/{id}/billets")
    public List<TicketResponse> listByReservation(@PathVariable UUID id,
                                                  @RequestParam UUID clientId) {
        return ticketService.listByReservation(id, clientId);
    }

    @GetMapping("/clients/{clientId}/billets")
    public PagedModel<TicketResponse> listByClient(
            @PathVariable UUID clientId,
            @PageableDefault(size = 10, sort = "dateEmission", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(ticketService.listByClient(clientId, pageable));
    }
}