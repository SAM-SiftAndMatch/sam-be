package com.sam.be.modules.user.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.user.dto.request.ClientProfileRequest;
import com.sam.be.modules.user.dto.request.FreelancerProfileRequest;
import com.sam.be.modules.user.dto.response.ClientProfileResponse;
import com.sam.be.modules.user.dto.response.FreelancerProfileResponse;
import com.sam.be.modules.user.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
@Tag(name = "Profile Management", description = "APIs for managing Client and Freelancer profiles")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/freelancer/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Get current freelancer profile", description = "Retrieve profile and skills of the logged-in freelancer")
    public ApiResponse<FreelancerProfileResponse> getMyFreelancerProfile() {
        FreelancerProfileResponse response = profileService.getFreelancerProfile(SecurityUtils.getCurrentUserId());
        return ApiResponse.<FreelancerProfileResponse>builder().result(response).build();
    }

    @PutMapping("/freelancer/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Update current freelancer profile", description = "Update profile details and replace all skills for the logged-in freelancer")
    public ApiResponse<FreelancerProfileResponse> updateMyFreelancerProfile(@Valid @RequestBody FreelancerProfileRequest request) {
        FreelancerProfileResponse response = profileService.updateFreelancerProfile(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<FreelancerProfileResponse>builder().result(response).build();
    }

    @GetMapping("/client/me")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Get current client profile", description = "Retrieve profile details of the logged-in client")
    public ApiResponse<ClientProfileResponse> getMyClientProfile() {
        ClientProfileResponse response = profileService.getClientProfile(SecurityUtils.getCurrentUserId());
        return ApiResponse.<ClientProfileResponse>builder().result(response).build();
    }

    @PutMapping("/client/me")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Update current client profile", description = "Update company and industry details for the logged-in client")
    public ApiResponse<ClientProfileResponse> updateMyClientProfile(@Valid @RequestBody ClientProfileRequest request) {
        ClientProfileResponse response = profileService.updateClientProfile(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<ClientProfileResponse>builder().result(response).build();
    }
}