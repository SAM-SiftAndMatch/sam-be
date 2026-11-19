package com.sam.be.modules.feedback;

import static com.sam.be.modules.feedback.FeedbackDtos.*;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.common.security.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
@PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER')")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService service;

    @PostMapping
    @RateLimit(
            action = "platform-feedback",
            type = RateLimit.Type.USER_ID,
            limit = 5,
            duration = 3600)
    public ApiResponse<UserFeedback> create(@Valid @RequestBody CreateInput input) {
        return ApiResponse.<UserFeedback>builder()
                .result(service.create(SecurityUtils.getCurrentUserId(), input))
                .build();
    }

    @GetMapping("/mine")
    public ApiResponse<PageResponse<UserFeedback>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<UserFeedback>>builder()
                .result(service.mine(SecurityUtils.getCurrentUserId(), page, size))
                .build();
    }
}
