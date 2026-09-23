package com.sam.be.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record LoginRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
        @NotBlank(message = "Password is required") String password) {}
