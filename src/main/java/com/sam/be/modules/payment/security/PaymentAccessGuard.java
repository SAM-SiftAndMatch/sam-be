package com.sam.be.modules.payment.security;

import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.payment.repository.PaymentRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component("paymentAccessGuard")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class PaymentAccessGuard {

    PaymentRepository paymentRepository;

    /** Kiểm tra quyền xem giao dịch ký quỹ (Escrow): Phải là một bên trong hợp đồng hoặc Admin. */
    public boolean canAccess(UUID paymentId) {
        if (paymentId == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (currentUserId == null) {
            return false;
        }

        return paymentRepository
                .findById(paymentId)
                .map(
                        payment -> {
                            if (payment.getContract() == null) {
                                return false;
                            }
                            var contract = payment.getContract();
                            boolean isClient =
                                    contract.getClient() != null
                                            && currentUserId.equals(contract.getClient().getId());
                            boolean isFreelancer =
                                    contract.getFreelancer() != null
                                            && currentUserId.equals(
                                                    contract.getFreelancer().getId());
                            return isClient || isFreelancer;
                        })
                .orElse(false);
    }

    /**
     * Kiểm tra quyền giải ngân (Release Escrow): Chỉ có Client của hợp đồng hoặc Admin mới được
     * giải ngân.
     */
    public boolean canRelease(UUID paymentId) {
        if (paymentId == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (currentUserId == null) {
            return false;
        }

        return paymentRepository
                .findById(paymentId)
                .map(
                        payment ->
                                payment.getContract() != null
                                        && payment.getContract().getClient() != null
                                        && currentUserId.equals(
                                                payment.getContract().getClient().getId()))
                .orElse(false);
    }
}
