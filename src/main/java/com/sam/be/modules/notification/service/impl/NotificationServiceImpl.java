package com.sam.be.modules.notification.service.impl;

import com.sam.be.modules.notification.dto.NotificationMessage;
import com.sam.be.modules.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    // Khẩu pháo bắn dữ liệu Real-time của Spring
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendInviteNotification(
            UUID freelancerId, UUID jobId, String jobTitle, BigDecimal matchScore) {
        sendInviteNotification(freelancerId, jobId, jobTitle, matchScore, null);
    }

    @Override
    public void sendInviteNotification(
            UUID freelancerId,
            UUID jobId,
            String jobTitle,
            BigDecimal matchScore,
            UUID recommendationId) {
        // Đóng gói data
        NotificationMessage payload =
                NotificationMessage.builder()
                        .type("1_TOUCH_INVITE")
                        .jobId(jobId)
                        .jobTitle(jobTitle)
                        .matchScore(matchScore)
                        .recommendationId(recommendationId)
                        .message("Khách hàng vừa chọn bạn cho dự án này. Bấm nhận việc ngay!")
                        .timestamp(LocalDateTime.now())
                        .build();

        // Kênh phát thanh dành riêng cho từng Dev (Dựa vào freelancerId)
        String destination = "/topic/users/" + freelancerId + "/notifications";

        messagingTemplate.convertAndSend(destination, payload);

        log.info("🔔 [WEBSOCKET] Đã đẩy Noti tới kênh: {}", destination);
    }

    @Override
    public void sendPaymentNotification(
            UUID userId,
            UUID contractId,
            UUID paymentId,
            BigDecimal amount,
            String type,
            String message) {
        NotificationMessage payload =
                NotificationMessage.builder()
                        .type(type)
                        .contractId(contractId)
                        .paymentId(paymentId)
                        .amount(amount)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build();

        String destination = "/topic/users/" + userId + "/notifications";

        messagingTemplate.convertAndSend(destination, payload);

        log.info("🔔 [WEBSOCKET] Đã đẩy Noti thanh toán ({}) tới kênh: {}", type, destination);
    }

    @Override
    public void sendSubscriptionNotification(
            UUID userId, UUID subscriptionId, String type, String message) {
        NotificationMessage payload =
                NotificationMessage.builder()
                        .type(type)
                        .subscriptionId(subscriptionId)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build();

        String destination = "/topic/users/" + userId + "/notifications";

        messagingTemplate.convertAndSend(destination, payload);

        log.info("🔔 [WEBSOCKET] Đã đẩy Noti gói dịch vụ ({}) tới kênh: {}", type, destination);
    }

    @Override
    public void sendDevClaimNotification(
            UUID clientId, UUID jobId, String jobTitle, UUID recommendationId, String devName) {
        NotificationMessage payload =
                NotificationMessage.builder()
                        .type("DEV_CLAIM")
                        .jobId(jobId)
                        .jobTitle(jobTitle)
                        .recommendationId(recommendationId)
                        .actorName(devName)
                        .message(
                                "Dev "
                                        + devName
                                        + " muốn nói chuyện về dự án này. Bấm đồng ý để mở Chat!")
                        .timestamp(LocalDateTime.now())
                        .build();

        String destination = "/topic/users/" + clientId + "/notifications";
        messagingTemplate.convertAndSend(destination, payload);
        log.info("🔔 [WEBSOCKET] Đã đẩy Noti DEV_CLAIM tới kênh: {}", destination);
    }

    @Override
    public void sendChatOpenedNotification(
            UUID userId, UUID jobId, String jobTitle, UUID recommendationId, UUID roomId) {
        NotificationMessage payload =
                NotificationMessage.builder()
                        .type("CHAT_OPENED")
                        .jobId(jobId)
                        .jobTitle(jobTitle)
                        .recommendationId(recommendationId)
                        .roomId(roomId)
                        .message("Đã mở phòng Chat cho dự án này. Vào thương lượng ngay!")
                        .timestamp(LocalDateTime.now())
                        .build();

        String destination = "/topic/users/" + userId + "/notifications";
        messagingTemplate.convertAndSend(destination, payload);
        log.info("🔔 [WEBSOCKET] Đã đẩy Noti CHAT_OPENED tới kênh: {}", destination);
    }

    @Override
    public void sendProposalNotification(
            UUID userId,
            UUID jobId,
            String jobTitle,
            UUID proposalId,
            String type,
            String message) {
        NotificationMessage payload =
                NotificationMessage.builder()
                        .type(type)
                        .jobId(jobId)
                        .jobTitle(jobTitle)
                        .proposalId(proposalId)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build();

        String destination = "/topic/users/" + userId + "/notifications";
        messagingTemplate.convertAndSend(destination, payload);
        log.info("🔔 [WEBSOCKET] Đã đẩy Noti proposal ({}) tới kênh: {}", type, destination);
    }
}
