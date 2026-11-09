package com.sam.be.modules.payment.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.payment.service.CheckoutService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/checkouts")
public class CheckoutController {
    private final CheckoutService checkouts;
    private final com.sam.be.modules.payment.service.CheckoutRetryService retries;
    private final com.sam.be.modules.payment.service.CheckoutRecoveryService recovery;
    private final com.sam.be.modules.payment.service.PaymentReconciliationService reconciliation;
    private final com.sam.be.modules.user.repository.UserRepository users;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;

    @GetMapping("/admin/cases")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> cases(@RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 100000) throw new IllegalArgumentException("Invalid page");
        return ApiResponse.builder().result(reconciliation.list(page)).build();
    }

    @GetMapping("/admin/cases/{id}/events")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> events(@PathVariable UUID id) {
        return ApiResponse.builder().result(reconciliation.history(id)).build();
    }

    public record CaseAction(
            @jakarta.validation.constraints.NotBlank String action,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200)
                    String password,
            @jakarta.validation.constraints.Size(max = 500) String evidenceReference) {
        @Override
        public String toString() {
            return "CaseAction[REDACTED]";
        }
    }

    @PostMapping("/admin/cases/{id}/actions")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @com.sam.be.common.security.annotation.RateLimit(
            type = com.sam.be.common.security.annotation.RateLimit.Type.USER_ID,
            action = "payment_case_action",
            limit = 5,
            duration = 60,
            failClosed = true)
    public ApiResponse<?> action(
            @PathVariable UUID id, @jakarta.validation.Valid @RequestBody CaseAction input) {
        UUID actor = SecurityUtils.getCurrentUserId();
        var admin =
                users.findById(actor).orElseThrow(com.sam.be.modules.finance.FinanceStore::missing);
        if (!Boolean.TRUE.equals(admin.getIsActive())
                || admin.getPasswordHash() == null
                || !passwords.matches(input.password(), admin.getPasswordHash()))
            throw com.sam.be.modules.finance.FinanceStore.forbidden();
        reconciliation.action(actor, id, input.action(), input.evidenceReference());
        return ApiResponse.builder().result(Map.of("recorded", true)).build();
    }

    @PostMapping("/{id}/retry")
    @com.sam.be.common.security.annotation.RateLimit(
            type = com.sam.be.common.security.annotation.RateLimit.Type.USER_ID,
            action = "checkout_retry",
            limit = 3,
            duration = 60,
            failClosed = true)
    public ApiResponse<CheckoutService.Checkout> retry(@PathVariable UUID id) {
        return ApiResponse.<CheckoutService.Checkout>builder()
                .result(recovery.retry(SecurityUtils.getCurrentUserId(), id))
                .build();
    }

    @PostMapping("/{id}/prepare-retry")
    @com.sam.be.common.security.annotation.RateLimit(
            type = com.sam.be.common.security.annotation.RateLimit.Type.USER_ID,
            action = "checkout_prepare_retry",
            limit = 3,
            duration = 60,
            failClosed = true)
    public ApiResponse<CheckoutService.Checkout> prepareRetry(@PathVariable UUID id) {
        return ApiResponse.<CheckoutService.Checkout>builder()
                .result(retries.prepare(SecurityUtils.getCurrentUserId(), id))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<CheckoutService.Checkout> get(@PathVariable UUID id) {
        return ApiResponse.<CheckoutService.Checkout>builder()
                .result(checkouts.get(SecurityUtils.getCurrentUserId(), id))
                .build();
    }

    @GetMapping("/me")
    public ApiResponse<List<CheckoutService.Checkout>> mine(
            @RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 100000) throw new IllegalArgumentException("Invalid page");
        return ApiResponse.<List<CheckoutService.Checkout>>builder()
                .result(checkouts.history(SecurityUtils.getCurrentUserId(), page))
                .build();
    }

    @GetMapping("/admin/review")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> review(@RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 100000) throw new IllegalArgumentException("Invalid page");
        return ApiResponse.builder().result(checkouts.review(page)).build();
    }
}
