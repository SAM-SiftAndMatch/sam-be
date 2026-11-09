package com.sam.be.modules.user.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sam.be.common.exception.ApiException;
import com.sam.be.modules.auth.dto.request.RegisterRequest;
import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.user.dto.request.ClientProfileRequest;
import com.sam.be.modules.user.dto.request.FreelancerProfileRequest;
import com.sam.be.modules.user.entity.User;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParticipationPolicyTest {
    private User completeUser() {
        return User.builder()
                .contractName("Nguyễn Văn A")
                .contactEmail("a@example.test")
                .phoneNumber("0901234567")
                .policyAcceptedAt(Instant.now())
                .policyVersion(ParticipationPolicy.VERSION)
                .build();
    }

    @Test
    void minimumBudgetIsThreeMillionAndAcceptsLargerRanges() {
        assertDoesNotThrow(
                () ->
                        ParticipationPolicy.validateBudget(
                                new BigDecimal("3000000"), new BigDecimal("3000000")));
        assertDoesNotThrow(
                () ->
                        ParticipationPolicy.validateBudget(
                                new BigDecimal("3000000"), new BigDecimal("5000000")));
        assertThrows(
                ApiException.class,
                () ->
                        ParticipationPolicy.validateBudget(
                                new BigDecimal("2999999"), new BigDecimal("5000000")));
        assertThrows(
                ApiException.class,
                () ->
                        ParticipationPolicy.validateBudget(
                                new BigDecimal("5000000"), new BigDecimal("3000000")));
        assertThrows(
                ApiException.class,
                () -> ParticipationPolicy.validateBudget(null, new BigDecimal("3000000")));
        assertThrows(
                ApiException.class,
                () ->
                        ParticipationPolicy.validateBudget(
                                new BigDecimal("3000000.5"), new BigDecimal("5000000")));
    }

    @Test
    void acceptingCurrentPolicyKeepsOriginalTimestamp() {
        User user = completeUser();
        Instant accepted = user.getPolicyAcceptedAt();
        ParticipationPolicy.accept(user);
        assertEquals(accepted, user.getPolicyAcceptedAt());
        user.setPolicyVersion("older");
        ParticipationPolicy.accept(user);
        assertEquals(ParticipationPolicy.VERSION, user.getPolicyVersion());
        assertNotNull(user.getPolicyAcceptedAt());
    }

    @Test
    void existingAccountWithoutParticipationConsentCannotJoinProjects() {
        User user = completeUser();
        assertDoesNotThrow(() -> ParticipationPolicy.requireCompleteProfile(user));
        user.setPhoneNumber("  ");
        assertThrows(ApiException.class, () -> ParticipationPolicy.requireCompleteProfile(user));
        User withoutConsent = completeUser();
        withoutConsent.setPolicyAcceptedAt(null);
        assertThrows(
                ApiException.class,
                () -> ParticipationPolicy.requireCompleteProfile(withoutConsent));
        User outdated = completeUser();
        outdated.setPolicyVersion("older");
        assertThrows(
                ApiException.class, () -> ParticipationPolicy.requireCompleteProfile(outdated));
    }

    @Test
    void profileAndRegistrationValidationRequireExplicitConsentAndContactDetails() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var client =
                    ClientProfileRequest.builder()
                            .contractName("Nguyễn A")
                            .phoneNumber("0901234567")
                            .contactEmail("a@example.test")
                            .acceptedTerms(true)
                            .build();
            assertTrue(validator.validate(client).isEmpty());
            client.setAcceptedTerms(false);
            assertFalse(validator.validate(client).isEmpty());
            client.setAcceptedTerms(null);
            assertFalse(validator.validate(client).isEmpty());
            client.setAcceptedTerms(true);
            client.setContactEmail("invalid");
            assertFalse(validator.validate(client).isEmpty());
            client.setContactEmail("a@example.test");
            client.setPhoneNumber("abc");
            assertFalse(validator.validate(client).isEmpty());
            var freelancer =
                    FreelancerProfileRequest.builder()
                            .headline("Developer")
                            .contractName("Nguyễn A")
                            .phoneNumber("+84 901 234 567")
                            .contactEmail("a@example.test")
                            .acceptedTerms(true)
                            .build();
            assertTrue(validator.validate(freelancer).isEmpty());
            freelancer.setContractName(" ");
            assertFalse(validator.validate(freelancer).isEmpty());
            var registration =
                    RegisterRequest.builder()
                            .fullName("Nguyễn A")
                            .email("a@example.test")
                            .password("example123")
                            .accountType(com.sam.be.common.constant.enums.UserRole.FREELANCER)
                            .acceptedTerms(false)
                            .build();
            assertTrue(
                    validator.validate(registration).stream()
                            .anyMatch(v -> v.getPropertyPath().toString().equals("acceptedTerms")));
            var job =
                    JobCreateRequest.builder()
                            .title("Project")
                            .description("Description")
                            .budgetMin(new BigDecimal("3000000"))
                            .budgetMax(new BigDecimal("3000000"))
                            .estimatedDurationMonths(BigDecimal.ONE)
                            .build();
            assertTrue(validator.validate(job).isEmpty());
            job.setBudgetMin(new BigDecimal("2999999"));
            assertFalse(validator.validate(job).isEmpty());
        }
    }
}
