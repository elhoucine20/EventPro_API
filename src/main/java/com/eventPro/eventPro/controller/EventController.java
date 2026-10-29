package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.event.EventRequest;
import com.eventPro.eventPro.dto.event.EventResponse;
import com.eventPro.eventPro.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;


    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create Event")
    public EventResponse create(@Valid @RequestBody EventRequest request){
        return eventService.create(request);
    }

    @PutMapping("/events/{id}")
    @Operation(summary = "Modifier Event")
    public EventResponse update(@PathVariable UUID id, @Valid @RequestBody EventRequest request){
        return eventService.update(id,request);
    }

    // temporaire //
    @PatchMapping("/events/{id}/publier")
    @Operation(summary = "PUBLISHED Event ")
    public EventResponse publier(@PathVariable UUID id,
                                 @RequestParam UUID organisateurId) {
        return eventService.publier(id, organisateurId);
    }

    @PatchMapping("/events/{id}/annuler")
    @Operation(summary = "CANCELLED Event ")
    public EventResponse annuler(@PathVariable UUID id,
                                 @RequestParam UUID organisateurId) {
        return eventService.annuler(id, organisateurId);
    }

    @GetMapping("/events/{id}")
    @Operation(summary = "Lister By Id ")
    public EventResponse getById(@PathVariable UUID id) {
        return eventService.getById(id);
    }

    @GetMapping("/events")
    @Operation(summary = "Lister tout events ")
    public PagedModel<EventResponse> listPublished(@PageableDefault(size = 10,sort = "dateDebut") Pageable pageable){
        return new PagedModel<>(eventService.listPublished(pageable));
    }

    @GetMapping("/organisateurs/{id}/events")
    @Operation(summary = "Lister by organisateur id")
    public PagedModel<EventResponse> listByOrganisateur(@PathVariable UUID id,@PageableDefault(size = 10,sort = "dateDebut") Pageable pageable){
        return new PagedModel<>(eventService.listByOrganisateur(id,pageable));
    }


}
