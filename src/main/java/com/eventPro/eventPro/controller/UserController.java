package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.user.RegisterClientRequest;
import com.eventPro.eventPro.dto.user.RegisterOrganisateurRequest;
import com.eventPro.eventPro.dto.user.UserResponse;
import com.eventPro.eventPro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    @PostMapping("/auth/register/client")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register de client")
    public UserResponse registerClient( @Valid @RequestBody RegisterClientRequest request){
        return userService.registerClient(request);
    }

    @PostMapping("/auth/register/organisateur")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register de organisateur")
    public UserResponse registerOrganisateur(@Valid @RequestBody RegisterOrganisateurRequest request){
        return userService.registerOrganisateur(request);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Lister by id")
    public UserResponse getById(@PathVariable UUID id){
        return userService.getById(id);
    }
    @GetMapping("/users")
    public List<UserResponse> getAll(){
        return userService.getAll();
    }
}
