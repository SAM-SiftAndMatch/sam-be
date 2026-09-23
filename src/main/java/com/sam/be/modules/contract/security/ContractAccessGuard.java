package com.sam.be.modules.contract.security;

import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.contract.repository.ContractRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component("contractAccessGuard")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ContractAccessGuard {

    ContractRepository contractRepository;

    /**
     * Kiểm tra người dùng có phải là một bên tham gia Hợp đồng (Client hoặc Freelancer) hay không.
     */
    public boolean isParticipant(UUID contractId) {
        if (contractId == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (currentUserId == null) {
            return false;
        }

        return contractRepository
                .findById(contractId)
                .map(
                        c -> {
                            boolean isClient =
                                    c.getClient() != null
                                            && currentUserId.equals(c.getClient().getId());
                            boolean isFreelancer =
                                    c.getFreelancer() != null
                                            && currentUserId.equals(c.getFreelancer().getId());
                            return isClient || isFreelancer;
                        })
                .orElse(false);
    }

    /**
     * Kiểm tra người dùng có phải là bên Client của hợp đồng hay không (dùng khi nghiệm thu, ký
     * quỹ).
     */
    public boolean isClient(UUID contractId) {
        if (contractId == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (currentUserId == null) {
            return false;
        }

        return contractRepository
                .findById(contractId)
                .map(c -> c.getClient() != null && currentUserId.equals(c.getClient().getId()))
                .orElse(false);
    }

    /**
     * Kiểm tra người dùng có phải là bên Freelancer của hợp đồng hay không (dùng khi nộp bàn giao
     * mã nguồn).
     */
    public boolean isFreelancer(UUID contractId) {
        if (contractId == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (currentUserId == null) {
            return false;
        }

        return contractRepository
                .findById(contractId)
                .map(
                        c ->
                                c.getFreelancer() != null
                                        && currentUserId.equals(c.getFreelancer().getId()))
                .orElse(false);
    }
}
