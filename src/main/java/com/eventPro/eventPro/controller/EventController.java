package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.event.EventRequest;
import com.eventPro.eventPro.dto.event.EventResponse;
import com.eventPro.eventPro.service.EventService;
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
    public EventResponse create(@Valid @RequestBody EventRequest request){
        return eventService.create(request);
    }

    @PutMapping("/events/{id}")
    public EventResponse update(@PathVariable UUID id, @Valid @RequestBody EventRequest request){
        return eventService.update(id,request);
    }

    // temporaire //
    @PatchMapping("/events/{id}/publier")
    public EventResponse publier(@PathVariable UUID id,
                                 @RequestParam UUID organisateurId) {
        return eventService.publier(id, organisateurId);
    }

    @PatchMapping("/events/{id}/annuler")
    public EventResponse annuler(@PathVariable UUID id,
                                 @RequestParam UUID organisateurId) {
        return eventService.annuler(id, organisateurId);
    }

    @GetMapping("/events/{id}")
    public EventResponse getById(@PathVariable UUID id) {
        return eventService.getById(id);
    }

    @GetMapping("/events")
    public PagedModel<EventResponse> listPublished(@PageableDefault(size = 10,sort = "dateDebut") Pageable pageable){
        return new PagedModel<>(eventService.listPublished(pageable));
    }

    @GetMapping("/organisateurs/{id}/events")
    public PagedModel<EventResponse> listByOrganisateur(@PathVariable UUID id,@PageableDefault(size = 10,sort = "dateDebut") Pageable pageable){
        return new PagedModel<>(eventService.listByOrganisateur(id,pageable));
    }


}
