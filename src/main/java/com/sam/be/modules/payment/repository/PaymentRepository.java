package com.sam.be.modules.payment.repository;

import com.sam.be.common.constant.enums.PaymentStatus;
import com.sam.be.common.constant.enums.PaymentType;
import com.sam.be.modules.payment.entity.Payment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByContractId(UUID contractId);

    List<Payment> findAllByContractId(UUID contractId);

    List<Payment> findAllByContractIdAndPaymentType(UUID contractId, PaymentType paymentType);

    Optional<Payment> findByPaymentGatewayId(String paymentGatewayId);

    interface EscrowTotals {
        BigDecimal getHeldFunds();

        BigDecimal getReleasedFunds();

        BigDecimal getLockedDeposit();

        Long getFundedProjectCount();
    }

    @Query(
            "SELECT "
                    + "SUM(CASE WHEN p.paymentType = :fundType AND p.status = :held THEN p.amount ELSE 0 END) AS heldFunds, "
                    + "SUM(CASE WHEN p.paymentType = :fundType AND p.status = :released THEN p.amount ELSE 0 END) AS releasedFunds, "
                    + "SUM(CASE WHEN p.paymentType = :depositType AND p.status = :held THEN p.amount ELSE 0 END) AS lockedDeposit, "
                    + "COUNT(DISTINCT CASE WHEN p.paymentType = :fundType AND p.status = :held THEN p.contract.id ELSE NULL END) AS fundedProjectCount "
                    + "FROM Payment p WHERE p.contract.freelancer.id = :freelancerId")
    EscrowTotals summarizeFreelancerEscrow(
            @Param("freelancerId") UUID freelancerId,
            @Param("fundType") PaymentType fundType,
            @Param("depositType") PaymentType depositType,
            @Param("held") PaymentStatus held,
            @Param("released") PaymentStatus released);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.contract.id = :contractId")
    List<Payment> findAllByContractIdForSettlement(@Param("contractId") UUID contractId);
}
