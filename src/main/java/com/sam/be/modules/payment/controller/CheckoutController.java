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
