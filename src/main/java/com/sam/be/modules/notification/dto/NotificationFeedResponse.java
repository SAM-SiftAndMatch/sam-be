package com.sam.be.modules.notification.dto;

import java.util.List;

public record NotificationFeedResponse(List<UserNotificationResponse> items, long unreadCount) {}
