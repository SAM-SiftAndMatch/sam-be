package com.sam.be.modules.job.security;

import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.job.repository.JobRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component("jobAccessGuard")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class JobAccessGuard {

    JobRepository jobRepository;

    /**
     * Kiểm tra người dùng hiện tại có phải chủ bài đăng Job (Client) hoặc Quản trị viên (Admin) hay
     * không. Sử dụng trong @PreAuthorize("@jobAccessGuard.isOwner(#id)")
     */
    public boolean isOwner(UUID jobId) {
        if (jobId == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (currentUserId == null) {
            return false;
        }

        return jobRepository
                .findById(jobId)
                .map(
                        job ->
                                job.getClient() != null
                                        && currentUserId.equals(job.getClient().getId()))
                .orElse(false);
    }
}
