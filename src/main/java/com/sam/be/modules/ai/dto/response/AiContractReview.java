package com.sam.be.modules.ai.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** Kết quả AI thẩm định hợp đồng sau khi 2 bên ký. */
@Data
public class AiContractReview {
    // Tổng tiền trích từ văn bản (null nếu không tìm thấy)
    private BigDecimal extractedAmount;
    // Số trong văn bản có khớp agreedAmount không
    private Boolean textMatchesField;
    // Số có đổi so với giá AI đề xuất ban đầu không
    private Boolean changedFromInitial;
    // Các điểm chưa hợp lý AI phát hiện (rỗng nếu không có)
    private List<String> issues;
    // OK: chốt luôn. NEEDS_CONFIRM: hỏi 2 bên giữ nguyên hay sửa lại
    private String verdict;
    // Câu nhắn post thẳng vào chat cho 2 bên cùng đọc
    private String chatMessage;
}
