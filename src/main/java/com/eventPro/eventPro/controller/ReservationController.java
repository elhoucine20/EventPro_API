package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.reservation.ReservationRequest;
import com.eventPro.eventPro.dto.reservation.ReservationResponse;
import com.eventPro.eventPro.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/events/{eventId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create reservation")
    public ReservationResponse create(@PathVariable UUID eventId, @Valid @RequestBody ReservationRequest request){
        return reservationService.create(eventId,request);
    }

    @GetMapping("/reservations/{id}")
    @Operation(summary = "Lister by id")
    public ReservationResponse getById(@PathVariable UUID id,@RequestParam UUID clientId){
        return reservationService.getById(id,clientId);
    }

    @GetMapping("/clients/{clientId}/reservations")
    @Operation(summary = "Lister by client id")
    public PagedModel<ReservationResponse> listByClient(@PathVariable UUID clientId,
                                                  @PageableDefault(size = 10,sort = "dateCreation",direction = Sort.Direction.DESC)
                                                  Pageable pageable){

        return new PagedModel<>(reservationService.listByClient(clientId,pageable));
    }

    @PatchMapping("/reservations/{id}/annuler")
    @Operation(summary = "annulation de reservation")
    public ReservationResponse annuler(@PathVariable UUID id,@RequestParam UUID clientId){
        return reservationService.annuler(id,clientId);
    }

}
