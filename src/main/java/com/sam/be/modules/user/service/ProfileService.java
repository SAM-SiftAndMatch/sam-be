package com.sam.be.modules.user.service;

import com.sam.be.modules.user.dto.request.ClientProfileRequest;
import com.sam.be.modules.user.dto.request.FreelancerProfileRequest;
import com.sam.be.modules.user.dto.response.ClientProfileResponse;
import com.sam.be.modules.user.dto.response.FreelancerProfileResponse;

import java.util.UUID;

public interface ProfileService {
    FreelancerProfileResponse getFreelancerProfile(UUID userId);
    FreelancerProfileResponse updateFreelancerProfile(UUID userId, FreelancerProfileRequest request);
    ClientProfileResponse getClientProfile(UUID userId);
    ClientProfileResponse updateClientProfile(UUID userId, ClientProfileRequest request);
}