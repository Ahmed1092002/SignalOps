package com.signalops.auth.app;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank
        @Pattern(
            regexp = "^(?=.*\\p{L})(?=.*\\d).{10,72}$",
            message = "Password must be 10-72 characters and contain at least one letter and one digit")
        String password,
    @NotBlank @Size(min = 1, max = 120) String fullName,
    @NotBlank @Size(min = 2, max = 80) String organizationName) {}
