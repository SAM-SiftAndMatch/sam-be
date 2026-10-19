package com.sam.be.modules.notification.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.notification.dto.NotificationFeedResponse;
import com.sam.be.modules.notification.dto.UserNotificationResponse;
import com.sam.be.modules.notification.service.UserNotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER')")
public class UserNotificationController {
    private final UserNotificationService service;

    @GetMapping("/recent")
    public ApiResponse<NotificationFeedResponse> recent() {
        return ApiResponse.<NotificationFeedResponse>builder()
                .result(service.getRecent(SecurityUtils.getCurrentUserId())).build();
    }

    @GetMapping("/page")
    public ApiResponse<PageResponse<UserNotificationResponse>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<UserNotificationResponse>>builder()
                .result(service.getPage(SecurityUtils.getCurrentUserId(), page, size)).build();
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID id) {
        service.markRead(SecurityUtils.getCurrentUserId(), id);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        service.markAllRead(SecurityUtils.getCurrentUserId());
        return ApiResponse.<Void>builder().build();
    }
}
