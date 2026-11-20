package com.sam.be.modules.feedback;

import static com.sam.be.modules.feedback.FeedbackDtos.*;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.util.SecurityUtils;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/feedback")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminFeedbackController {
    private final FeedbackService service;

    @GetMapping
    public ApiResponse<PageResponse<AdminFeedback>> list(
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<PageResponse<AdminFeedback>>builder()
                .result(service.list(rating, status, q, page, size))
                .build();
    }

    @GetMapping("/summary")
    public ApiResponse<Summary> summary() {
        return ApiResponse.<Summary>builder().result(service.summary()).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminFeedback> detail(@PathVariable UUID id) {
        return ApiResponse.<AdminFeedback>builder().result(service.detail(id)).build();
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminFeedback> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateInput input) {
        return ApiResponse.<AdminFeedback>builder()
                .result(service.update(SecurityUtils.getCurrentUserId(), id, input))
                .build();
    }
}
