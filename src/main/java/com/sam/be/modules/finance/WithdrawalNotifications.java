package com.sam.be.modules.finance;

import static com.sam.be.modules.finance.FinanceStore.*;

import com.sam.be.modules.notification.dto.NotificationMessage;
import com.sam.be.modules.notification.service.UserNotificationService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WithdrawalNotifications {
    private final FinanceStore store;
    private final UserNotificationService notifications;

    public void changed(Map<String, Object> request, String status) {
        if (!"EARLY".equals(request.get("requestType"))) return;
        var p =
                store.one(
                        "SELECT p.owner_id,p.client_id,p.job_id,j.title FROM finance_payables p JOIN jobs j ON j.id=p.job_id WHERE p.id=?",
                        id(request, "earlyPayableId"));
        UUID owner = id(p, "ownerId"), job = id(p, "jobId");
        String title = p.get("title").toString();
        if ("REQUESTED".equals(status)) {
            for (var admin :
                    store.rows(
                            "SELECT id FROM users WHERE role='ADMIN' AND is_active=true ORDER BY id"))
                send(
                        id(admin, "id"),
                        job,
                        title,
                        "EARLY_WITHDRAWAL_REQUESTED",
                        "Có yêu cầu rút tiền sớm cần xét duyệt: " + title);
        } else {
            String label =
                    switch (status) {
                        case "APPROVED" -> "được duyệt, đang chờ chuyển khoản";
                        case "PAID" -> "đã được chi trả";
                        case "REJECTED" -> "bị từ chối";
                        case "FAILED" -> "đã xác nhận chuyển khoản thất bại";
                        case "CANCELLED" -> "đã hủy";
                        default -> "được cập nhật";
                    };
            send(
                    owner,
                    job,
                    title,
                    "EARLY_WITHDRAWAL_UPDATED",
                    "Yêu cầu rút sớm " + label + ": " + title);
            if ("APPROVED".equals(status))
                send(
                        id(p, "clientId"),
                        job,
                        title,
                        "EARLY_WITHDRAWAL_APPROVED",
                        "SAM đã duyệt chi sớm cho freelancer. Thời hạn và nghĩa vụ bảo trì của dự án vẫn giữ nguyên: "
                                + title);
        }
    }

    private void send(UUID recipient, UUID job, String title, String type, String message) {
        notifications.persistAndPublish(
                recipient,
                NotificationMessage.builder()
                        .type(type)
                        .jobId(job)
                        .jobTitle(title)
                        .message(message)
                        .build());
    }
}
