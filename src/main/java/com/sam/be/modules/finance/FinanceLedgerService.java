package com.sam.be.modules.finance;

import static com.sam.be.modules.finance.FinanceStore.*;

import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.payment.entity.Payment;
import com.sam.be.modules.wallet.entity.ProjectSettlement;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinanceLedgerService {
    private final FinanceStore store;

    public boolean isLive(Payment p) {
        return p.getVerifiedAt() != null && "LIVE".equals(p.getEnvironment());
    }

    @Transactional
    public void recordSettlement(
            Contract contract, ProjectSettlement settlement, Payment fund, Payment deposit) {
        UUID owner = contract.getFreelancer().getId();
        store.lockOwner(owner);
        boolean sandbox = !(isLive(fund) && isLive(deposit));
        // Legacy unverified payments remain simulation-only; never turn them into withdrawable
        // cash.
        String prefix = sandbox ? "sandbox:" : "live:";
        if (sandbox)
            for (Payment p : List.of(fund, deposit))
                if (p.getVerifiedAt() == null) {
                    store.journal(
                            "legacy-simulation-source:" + p.getId(),
                            contract.getId(),
                            owner,
                            "Nguồn mô phỏng chưa xác minh",
                            true,
                            debit(prefix + "cash:simulation", p.getAmount()),
                            credit(prefix + "escrow:" + p.getId(), p.getAmount()));
                }
        var accepted = settlement.getCompletedAt().toInstant(java.time.ZoneOffset.UTC);
        store.jdbc().update(
                """
            INSERT INTO finance_payables
            (id,settlement_id,contract_id,job_id,owner_id,client_id,amount,earnings,deposit_refund,sandbox,accepted_at,available_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """,
                UUID.randomUUID(),
                settlement.getId(),
                contract.getId(),
                contract.getJob().getId(),
                owner,
                contract.getClient().getId(),
                settlement.getFreelancerPayout().add(settlement.getDepositRefund()),
                settlement.getFreelancerPayout(),
                settlement.getDepositRefund(),
                sandbox,
                store.time(accepted),
                store.time(accepted.plus(FinancePolicy.WARRANTY)));
        store.journal(
                "settlement:" + settlement.getId(),
                contract.getId(),
                owner,
                "Nghiệm thu - công nợ chờ bảo trì 7 ngày",
                sandbox,
                debit(prefix + "escrow:" + fund.getId(), fund.getAmount()),
                debit(prefix + "escrow:" + deposit.getId(), deposit.getAmount()),
                credit(
                        prefix + "payable:" + owner,
                        settlement.getFreelancerPayout().add(settlement.getDepositRefund())),
                credit(prefix + "platform-revenue", settlement.getPlatformFee()));
        store.audit(
                contract.getClient().getId(),
                owner,
                settlement.getId(),
                "SETTLEMENT_POSTED",
                "Công nợ chờ hết 168 giờ bảo trì");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> wallet(UUID owner) {
        List<Map<String, Object>> payables = store.payables(owner);
        BigDecimal available = BigDecimal.ZERO,
                pending = BigDecimal.ZERO,
                held = BigDecimal.ZERO,
                reserved = BigDecimal.ZERO,
                paid = BigDecimal.ZERO,
                simulation = BigDecimal.ZERO;
        for (Map<String, Object> p : payables) {
            BigDecimal remainder = store.remaining(p);
            if ((Boolean) p.get("sandbox")) {
                simulation = simulation.add(remainder);
                continue;
            }
            reserved = reserved.add(money(p, "reserved"));
            paid = paid.add(money(p, "paid"));
            if ((Boolean) p.get("blocked")) held = held.add(remainder);
            else if (store.now().isBefore(instant(p, "availableAt")))
                pending = pending.add(remainder);
            else available = available.add(remainder);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currency", "VND");
        result.put("availableBalance", available);
        result.put("pendingBalance", pending);
        result.put("heldBalance", held);
        result.put("reservedBalance", reserved);
        result.put("paidTotal", paid);
        result.put("sandboxBalance", simulation);
        var escrow =
                store.one(
                        "SELECT COALESCE(SUM(CASE WHEN payment_type IN ('CONTRACT_FUND','MAINTENANCE_FUND') THEN amount ELSE 0 END),0) AS gross,COALESCE(SUM(CASE WHEN payment_type='SECURITY_DEPOSIT' THEN amount ELSE 0 END),0) AS deposit FROM payments WHERE contract_id IN (SELECT id FROM contracts WHERE freelancer_id=?) AND status='HELD_IN_ESCROW' AND environment='LIVE' AND verified_at IS NOT NULL",
                        owner);
        result.put("escrowGross", escrow.get("gross"));
        result.put("lockedDeposit", escrow.get("deposit"));
        result.put("sandbox", false);
        result.put("payables", payables);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID owner, int page) {
        return store.rows(
                "SELECT j.id,j.contract_id,j.description,j.sandbox,j.created_at,(SELECT COALESCE(SUM(e.debit),0) FROM finance_entries e WHERE e.journal_id=j.id) AS gross_amount,(SELECT COALESCE(SUM(e.credit-e.debit),0) FROM finance_entries e WHERE e.journal_id=j.id AND e.account_key IN ('live:payable:'||j.owner_id::text,'sandbox:payable:'||j.owner_id::text)) AS payable_change FROM finance_journals j WHERE j.owner_id=? ORDER BY j.created_at DESC,j.id DESC LIMIT 25 OFFSET ?",
                owner,
                Math.max(0, page) * 25L);
    }
}
