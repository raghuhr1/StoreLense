package com.storelense.auth.dto;

import jakarta.validation.constraints.*;

import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        UUID storeId,
        @NotEmpty Set<String> roles
) {}
