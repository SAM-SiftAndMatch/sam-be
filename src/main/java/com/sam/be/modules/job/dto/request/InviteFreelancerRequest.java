package com.sam.be.modules.job.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record InviteFreelancerRequest(@NotNull UUID freelancerId, @Size(max = 2000) String message) {}
