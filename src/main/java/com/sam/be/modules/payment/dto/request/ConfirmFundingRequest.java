package com.sam.be.modules.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FE tự báo đã chuyển tiền sau khi VNPay redirect về (chữa cháy khi IPN server-to-server không tới
 * được môi trường local). BE đối chiếu txnRef + amount với bản ghi rồi mới ghi nhận.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmFundingRequest {
    // vnp_TxnRef trên URL return của VNPay (đối chiếu với paymentGatewayId)
    private String txnRef;
    // vnp_Amount trên URL return (đơn vị xu = VND x 100, đối chiếu với amount)
    private Long amountVnd;
}
