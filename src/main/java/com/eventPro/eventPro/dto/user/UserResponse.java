package com.eventPro.eventPro.dto.user;

import com.eventPro.eventPro.model.enums.RoleUser;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        RoleUser role
) {}
