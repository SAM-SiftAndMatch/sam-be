package com.sam.be.modules.notification.service.impl;

import com.sam.be.modules.notification.dto.NotificationMessage;
import com.sam.be.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    // Khẩu pháo bắn dữ liệu Real-time của Spring
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendInviteNotification(UUID freelancerId, UUID jobId, String jobTitle, BigDecimal matchScore) {
        // Đóng gói data
        NotificationMessage payload = NotificationMessage.builder()
                .type("1_TOUCH_INVITE")
                .jobId(jobId)
                .jobTitle(jobTitle)
                .matchScore(matchScore)
                .message("Khách hàng vừa chọn bạn cho dự án này. Bấm nhận việc ngay!")
                .timestamp(LocalDateTime.now())
                .build();

        // Kênh phát thanh dành riêng cho từng Dev (Dựa vào freelancerId)
        String destination = "/topic/users/" + freelancerId + "/notifications";

        messagingTemplate.convertAndSend(destination, payload);

        log.info("🔔 [WEBSOCKET] Đã đẩy Noti tới kênh: {}", destination);
    }
}