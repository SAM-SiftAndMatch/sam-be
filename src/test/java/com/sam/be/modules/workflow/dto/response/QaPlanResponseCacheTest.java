package com.sam.be.modules.workflow.dto.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.config.AppConfig;
import com.sam.be.common.constant.enums.QaPlanStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QaPlanResponseCacheTest {

    @Test
    void readsQaPlanAndChecklistItemsFromCachedJson() throws Exception {
        ObjectMapper objectMapper = new AppConfig().objectMapper();
        QaChecklistItemResponse item =
                QaChecklistItemResponse.builder()
                        .id(UUID.randomUUID())
                        .sortOrder(1)
                        .requirementRef("SRS-1")
                        .title("Đăng nhập")
                        .testSteps("Mở trang đăng nhập")
                        .expectedResult("Đăng nhập thành công")
                        .priority("HIGH")
                        .build();
        QaPlanResponse original =
                QaPlanResponse.builder()
                        .id(UUID.randomUUID())
                        .deliveryId(UUID.randomUUID())
                        .status(QaPlanStatus.GENERATED)
                        .generatedAt(LocalDateTime.of(2026, 10, 13, 16, 47, 44))
                        .checklistItems(List.of(item))
                        .build();

        String cachedJson = objectMapper.writeValueAsString(original);
        QaPlanResponse restored = objectMapper.readValue(cachedJson, QaPlanResponse.class);

        assertEquals(original, restored);
    }
}
