package com.sam.be.modules.finance;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PaidMaintenanceController {
    private final PaidMaintenanceService service;
    private final PaymentService payments;

    public record QuoteInput(
            @NotNull @DecimalMin("1") @Digits(integer = 10, fraction = 0) BigDecimal amount,
            @Min(1) @Max(90) int deliveryDays,
            @NotBlank @Size(max = 8000) String scope) {}

    public record ActionInput(
            @NotBlank
                    @Pattern(
                            regexp =
                                    "START|SUBMIT|REVISION|ACCEPT|ESCALATE|ADMIN_RESUME|ADMIN_COMPLETE")
                    String action,
            @NotBlank @Size(max = 8000) String content) {}

    private static <T> ApiResponse<T> ok(T value) {
        return ApiResponse.<T>builder().result(value).build();
    }

    @GetMapping("/maintenance-tickets/{id}/quotes")
    public ApiResponse<?> quotes(@PathVariable UUID id) {
        return ok(service.quotes(SecurityUtils.getCurrentUserId(), id));
    }

    @PostMapping("/maintenance-tickets/{id}/quotes")
    @PreAuthorize("hasRole('FREELANCER')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "maintenance_quote",
            limit = 10,
            duration = 300)
    public ApiResponse<?> quote(@PathVariable UUID id, @Valid @RequestBody QuoteInput input) {
        return ok(
                service.quote(
                        SecurityUtils.getCurrentUserId(),
                        id,
                        input.amount(),
                        input.deliveryDays(),
                        input.scope()));
    }

    @PostMapping("/maintenance-quotes/{id}/accept")
    @PreAuthorize("hasRole('CLIENT')")
    public ApiResponse<?> accept(@PathVariable UUID id) {
        return ok(service.accept(SecurityUtils.getCurrentUserId(), id));
    }

    @GetMapping("/projects/{job}/paid-maintenance-orders")
    public ApiResponse<?> orders(@PathVariable UUID job) {
        return ok(service.orders(SecurityUtils.getCurrentUserId(), job));
    }

    @PostMapping("/paid-maintenance-orders/{id}/checkout")
    @PreAuthorize("hasRole('CLIENT')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "maintenance_checkout",
            limit = 5,
            duration = 60)
    public ApiResponse<?> checkout(@PathVariable UUID id) {
        return ok(payments.createMaintenanceFunding(SecurityUtils.getCurrentUserId(), id));
    }

    @PostMapping("/paid-maintenance-orders/{id}/actions")
    public ApiResponse<?> action(@PathVariable UUID id, @Valid @RequestBody ActionInput input) {
        return ok(
                service.action(
                        SecurityUtils.getCurrentUserId(), id, input.action(), input.content()));
    }
}
