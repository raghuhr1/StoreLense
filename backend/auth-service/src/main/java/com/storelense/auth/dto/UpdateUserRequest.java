package com.storelense.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record UpdateUserRequest(
        @Email String email,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        UUID storeId,
        Set<String> roles,
        Boolean active
) {}
