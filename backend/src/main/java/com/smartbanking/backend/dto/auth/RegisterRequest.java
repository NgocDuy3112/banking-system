package com.smartbanking.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(max = 255) String fullName,
    @NotBlank @Pattern(regexp = "\\d{12}", message = "must be exactly 12 digits") String citizenId,
    LocalDate dateOfBirth,
    @Size(max = 20) String phoneNumber
) {}