package com.sam.be.modules.job.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SelectFreelancerRequest(@NotNull UUID invitationId) {}
