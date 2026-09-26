package com.sam.be.modules.notification.service.impl;

import com.sam.be.modules.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void sendInviteNotification(UUID freelancerId, UUID jobId, String jobTitle, BigDecimal matchScore) {
        log.info("===================================================================");
        log.info(" [MOCK WEBSOCKET NOTI] -> GỬI THÔNG BÁO CHO DEV");
        log.info(" Freelancer ID: {}", freelancerId);
        log.info(" Job: {} (ID: {})", jobTitle, jobId);
        log.info(" Độ khớp: {}%", matchScore);
        log.info(" Lời nhắn: Khách hàng VNG vừa chọn bạn cho dự án này. Bấm nhận việc ngay!");
        log.info("===================================================================");
    }
}