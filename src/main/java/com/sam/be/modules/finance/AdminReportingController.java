package com.sam.be.modules.finance;

import com.sam.be.common.response.ApiResponse;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportingController {
    private final AdminReportingService reporting;

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard() {
        return ApiResponse.builder().result(reporting.overview()).build();
    }

    @GetMapping("/revenue")
    public ApiResponse<?> revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "") String source,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.builder()
                .result(reporting.revenue(from, to, source, projectId, q, page))
                .build();
    }

    @GetMapping("/users")
    public ApiResponse<?> users(
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.builder().result(reporting.users(role, q, status, page)).build();
    }
}
