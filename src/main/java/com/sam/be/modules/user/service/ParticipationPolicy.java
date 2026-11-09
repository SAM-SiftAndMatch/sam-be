package com.sam.be.modules.user.service;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.user.entity.User;
import java.math.BigDecimal;
import java.time.Instant;

public final class ParticipationPolicy {
    public static final String VERSION = "2026-11-09";
    public static final BigDecimal MINIMUM_PROJECT_BUDGET = new BigDecimal("3000000");
    public static final String CONTRACT_NOTICE =
            "Hai bên xác nhận thông tin liên hệ chính xác; "
                    + "đã đọc và đồng ý Điều khoản Dịch vụ và Chính sách Bảo mật SAM phiên bản "
                    + VERSION
                    + ". Client cung cấp yêu cầu, nạp tiền và nghiệm thu theo hợp đồng; freelancer thực hiện "
                    + "đúng phạm vi, thời hạn, bảo mật dữ liệu và chỉ bàn giao nội dung có quyền sử dụng. "
                    + "Mọi thay đổi phạm vi, giá và thời hạn cần được hai bên xác nhận trước khi thực hiện. "
                    + "Client nạp 100% giá trị hợp đồng; freelancer đóng cọc cam kết 2%. Thời hạn bảo trì "
                    + "là 168 giờ (7 ngày) kể từ nghiệm thu. Công nợ chỉ được giải phóng khi hết thời hạn "
                    + "bảo trì và không còn yêu cầu bảo trì hoặc tranh chấp chặn chi. "
                    + "Thanh toán, bàn giao, bảo trì và tranh chấp được xử lý qua quy trình trên nền tảng. "
                    + "Thông tin liên hệ được sử dụng để lập hợp đồng và phối hợp thực hiện dự án.";

    private ParticipationPolicy() {}

    public static void accept(User user) {
        if (!VERSION.equals(user.getPolicyVersion()) || user.getPolicyAcceptedAt() == null) {
            user.setPolicyVersion(VERSION);
            user.setPolicyAcceptedAt(Instant.now());
        }
    }

    public static void requireCompleteProfile(User user) {
        if (user == null
                || blank(user.getContractName())
                || blank(user.getPhoneNumber())
                || blank(user.getContactEmail())
                || user.getPolicyAcceptedAt() == null
                || !VERSION.equals(user.getPolicyVersion())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Vui lòng hoàn thiện họ tên, số điện thoại, email liên hệ và chấp nhận điều khoản trong hồ sơ trước khi tham gia dự án.");
        }
    }

    public static void validateBudget(BigDecimal min, BigDecimal max) {
        if (min == null
                || max == null
                || min.compareTo(MINIMUM_PROJECT_BUDGET) < 0
                || max.compareTo(min) < 0
                || min.stripTrailingZeros().scale() > 0
                || max.stripTrailingZeros().scale() > 0) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Ngân sách dự án tối thiểu là 3.000.000đ; ngân sách tối đa phải lớn hơn hoặc bằng tối thiểu và là số nguyên.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
