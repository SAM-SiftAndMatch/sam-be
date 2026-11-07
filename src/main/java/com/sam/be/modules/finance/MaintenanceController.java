package com.sam.be.modules.finance;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.common.security.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MaintenanceController {
    private final MaintenanceService service;

    public record TicketInput(
            @NotNull UUID requestKey,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 8000) String description,
            @Pattern(regexp = "WARRANTY|PAID") @NotNull String kind) {}

    public record TicketAction(
            @NotBlank
                    @Pattern(
                            regexp =
                                    "COMMENT|START|NEED_INFORMATION|SUBMIT_FIX|ACCEPT_FIX|REJECT_FIX|ESCALATE|WITHDRAW|ADMIN_RESOLVE|ADMIN_OUT_OF_SCOPE")
                    String action,
            @NotBlank @Size(max = 8000) String content) {}

    private static <T> ApiResponse<T> ok(T value) {
        return ApiResponse.<T>builder().result(value).build();
    }

    @GetMapping("/projects/{job}/maintenance")
    public ApiResponse<?> summary(@PathVariable UUID job) {
        return ok(service.summary(SecurityUtils.getCurrentUserId(), job));
    }

    @GetMapping("/projects/{job}/maintenance-tickets")
    public ApiResponse<?> tickets(
            @PathVariable UUID job,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(service.tickets(SecurityUtils.getCurrentUserId(), job, page));
    }

    @PostMapping("/projects/{job}/maintenance-tickets")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "maintenance_create",
            limit = 10,
            duration = 300)
    public ApiResponse<?> create(@PathVariable UUID job, @Valid @RequestBody TicketInput input) {
        return ok(
                service.create(
                        SecurityUtils.getCurrentUserId(),
                        job,
                        input.requestKey(),
                        input.kind(),
                        input.title(),
                        input.description()));
    }

    public record ExtensionInput(
            @NotNull java.time.Instant resolutionDueAt,
            @NotBlank @Size(max = 8000) String content) {}

    @PostMapping("/maintenance-tickets/{id}/extend")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> extend(@PathVariable UUID id, @Valid @RequestBody ExtensionInput input) {
        return ok(
                service.extend(
                        SecurityUtils.getCurrentUserId(),
                        id,
                        input.resolutionDueAt(),
                        input.content()));
    }

    @GetMapping("/maintenance-tickets/{id}")
    public ApiResponse<?> detail(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(service.detail(SecurityUtils.getCurrentUserId(), id, page));
    }

    @PostMapping("/maintenance-tickets/{id}/actions")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "maintenance_update",
            limit = 30,
            duration = 300)
    public ApiResponse<?> action(@PathVariable UUID id, @Valid @RequestBody TicketAction input) {
        return ok(
                service.action(
                        SecurityUtils.getCurrentUserId(), id, input.action(), input.content()));
    }
}
