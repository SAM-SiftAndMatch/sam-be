package com.sam.be.modules.auth.dto.request;

import com.sam.be.common.constant.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record RegisterRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
        @NotBlank(message = "Password is required")
                @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
                String password,
        @NotBlank(message = "Full name is required")
                @Size(max = 255, message = "Full name must not exceed 255 characters")
                String fullName,
        @NotNull(message = "Account type is required (FREELANCER or CLIENT)")
                UserRole accountType) {}
