package com.sam.be.modules.payment.service;

import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.payment.dto.response.PaymentResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse createEscrow(UUID userId, CreateEscrowRequest request);

    Map<String, String> handleVnpayIpn(Map<String, String> params);

    PaymentResponse releaseEscrow(UUID userId, UUID paymentId);

    List<PaymentResponse> getByContract(UUID userId, UUID contractId);
}
