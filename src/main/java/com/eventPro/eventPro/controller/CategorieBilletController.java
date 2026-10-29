package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.categorie.CategorieBilletRequest;
import com.eventPro.eventPro.dto.categorie.CategorieBilletResponse;
import com.eventPro.eventPro.service.CategorieBilletSercice;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CategorieBilletController {

    private final CategorieBilletSercice categorieBilletSercice;

    @PostMapping("/events/{eventId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create Categorie Bellit")
    public CategorieBilletResponse create(@PathVariable UUID eventId, @Valid @RequestBody CategorieBilletRequest request){
        return categorieBilletSercice.create(eventId,request);
    }

    @GetMapping("/events/{eventId}/categories")
    @Operation(summary = "Lister by event id")
    public List<CategorieBilletResponse> listByEvent(@PathVariable UUID eventId){
        return categorieBilletSercice.listByEvent(eventId);
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update Categorie Bellit")
    public CategorieBilletResponse update(@PathVariable UUID id,@Valid @RequestBody CategorieBilletRequest request){
        return categorieBilletSercice.update(id,request);
    }

    // TEMPORAIRE : organisateurId sera remplacé par l'utilisateur connecté
    @DeleteMapping("/categories/{id}")
    @Operation(summary = "Delete Categorie Bellit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,@RequestParam UUID organisateurId){
        categorieBilletSercice.delete(id,organisateurId);
    }
}
