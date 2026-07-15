package com.storelense.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Role name must be uppercase letters, digits, and underscores")
        String name,

        @Size(max = 500)
        String description
) {}
