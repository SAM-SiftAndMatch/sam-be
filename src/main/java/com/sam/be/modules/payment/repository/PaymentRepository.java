package com.sam.be.modules.payment.repository;

import com.sam.be.common.constant.enums.PaymentType;
import com.sam.be.modules.payment.entity.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByContractId(UUID contractId);

    List<Payment> findAllByContractId(UUID contractId);

    List<Payment> findAllByContractIdAndPaymentType(UUID contractId, PaymentType paymentType);

    Optional<Payment> findByPaymentGatewayId(String paymentGatewayId);
}
