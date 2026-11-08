package com.sam.be.modules.finance;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FinanceController {
    private final FinanceLedgerService ledger;
    private final FinanceIncomeService income;
    private final BankAccountService banks;
    private final WithdrawalService withdrawals;
    private final MaintenanceService maintenance;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final FinanceStore store;

    public record BankInput(
            @NotBlank @Size(max = 20) String bankCode,
            @NotBlank @Size(max = 40) String accountNumber,
            @NotBlank @Size(max = 150) String accountHolder,
            @NotBlank @Size(max = 200) String password) {
        @Override
        public String toString() {
            return "BankInput[REDACTED]";
        }
    }

    public record PasswordInput(@NotBlank @Size(max = 200) String password) {
        @Override
        public String toString() {
            return "PasswordInput[REDACTED]";
        }
    }

    public record DecisionInput(
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Size(max = 1000) String note,
            boolean approved) {
        @Override
        public String toString() {
            return "DecisionInput[REDACTED]";
        }
    }

    public record WithdrawInput(
            @NotNull UUID bankAccountId,
            @NotNull UUID requestKey,
            @NotNull @DecimalMin("1") @Digits(integer = 12, fraction = 0) BigDecimal amount,
            @NotBlank @Size(max = 200) String password) {
        @Override
        public String toString() {
            return "WithdrawInput[REDACTED]";
        }
    }

    public record PaymentProof(
            @NotBlank @Size(max = 200) String password,
            @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9._:/-]{3,150}")
                    String bankReference,
            @NotBlank @Size(min = 3, max = 300) String evidenceReference,
            @NotBlank @Size(max = 1000) String note) {
        @Override
        public String toString() {
            return "PaymentProof[REDACTED]";
        }
    }

    private UUID user() {
        return SecurityUtils.getCurrentUserId();
    }

    private void reauthenticate(String password) {
        var user = users.findById(user()).orElseThrow(FinanceStore::missing);
        if (user.getPasswordHash() == null)
            throw FinanceStore.invalid(
                    "Hãy thiết lập mật khẩu tài khoản qua chức năng Quên mật khẩu trước khi thực hiện giao dịch tài chính");
        if (!Boolean.TRUE.equals(user.getIsActive())
                || !passwords.matches(password, user.getPasswordHash()))
            throw FinanceStore.forbidden();
    }

    private static <T> ApiResponse<T> ok(T result) {
        return ApiResponse.<T>builder().result(result).build();
    }

    @GetMapping("/request-key")
    public ApiResponse<String> requestKey() {
        return ok(UUID.randomUUID().toString());
    }

    @GetMapping("/wallet")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<Map<String, Object>> wallet() {
        var result = ledger.wallet(user());
        result.put("withdrawalsEnabled", withdrawals.enabled());
        result.put("minimumWithdrawal", withdrawals.minimum());
        result.put("dailyWithdrawalLimit", withdrawals.dailyLimit());
        return ok(result);
    }

    @GetMapping("/income")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<?> income(
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return ok(income.summary(user(), year));
    }

    @GetMapping("/income/entries")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<?> incomeEntries(
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer year,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(income.entries(user(), year, page));
    }

    @GetMapping("/bank-accounts/setup-status")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<?> bankSetup() {
        return ok(banks.setupStatus(user()));
    }

    @PutMapping("/bank-accounts/{id}")
    @PreAuthorize("hasRole('FREELANCER')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> updateBank(@PathVariable UUID id, @Valid @RequestBody BankInput input) {
        reauthenticate(input.password());
        return ok(
                banks.replace(
                        user(),
                        id,
                        input.bankCode(),
                        input.accountNumber(),
                        input.accountHolder()));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<?> history(@RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(ledger.history(user(), page));
    }

    @GetMapping("/bank-accounts")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<?> banks() {
        return ok(banks.list(user()));
    }

    @PostMapping("/bank-accounts")
    @PreAuthorize("hasRole('FREELANCER')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> addBank(@Valid @RequestBody BankInput input) {
        reauthenticate(input.password());
        return ok(
                banks.add(user(), input.bankCode(), input.accountNumber(), input.accountHolder()));
    }

    @PostMapping("/bank-accounts/{id}/disable")
    @PreAuthorize("hasRole('FREELANCER')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> disable(@PathVariable UUID id, @Valid @RequestBody PasswordInput input) {
        reauthenticate(input.password());
        banks.disable(user(), id);
        return ok(true);
    }

    @GetMapping("/withdrawals")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<?> withdrawals(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(withdrawals.list(user(), page));
    }

    @GetMapping("/withdrawals/{id}")
    public ApiResponse<?> withdrawal(@PathVariable UUID id) {
        return ok(withdrawals.detail(user(), id, SecurityUtils.isAdmin()));
    }

    @PostMapping("/withdrawals")
    @PreAuthorize("hasRole('FREELANCER')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> withdraw(@Valid @RequestBody WithdrawInput input) {
        reauthenticate(input.password());
        return ok(
                withdrawals.create(
                        user(), input.bankAccountId(), input.requestKey(), input.amount()));
    }

    @PostMapping("/withdrawals/{id}/cancel")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<?> cancel(@PathVariable UUID id) {
        withdrawals.cancel(user(), id);
        return ok(true);
    }

    @GetMapping("/admin/bank-accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> adminBanks(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(banks.adminList(page));
    }

    @PostMapping("/admin/bank-accounts/{id}/reveal")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_admin_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> revealBank(
            @PathVariable UUID id, @Valid @RequestBody PasswordInput input) {
        reauthenticate(input.password());
        return ok(banks.reveal(user(), id));
    }

    @PostMapping("/admin/bank-accounts/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_admin_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> verify(@PathVariable UUID id, @Valid @RequestBody DecisionInput input) {
        reauthenticate(input.password());
        banks.verify(user(), id, input.approved(), input.note());
        return ok(true);
    }

    @GetMapping("/admin/withdrawals")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> adminWithdrawals(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(withdrawals.adminList(page));
    }

    @PostMapping("/admin/withdrawals/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_admin_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> approve(@PathVariable UUID id, @Valid @RequestBody PasswordInput input) {
        reauthenticate(input.password());
        withdrawals.approve(user(), id);
        return ok(true);
    }

    @PostMapping("/admin/withdrawals/{id}/start-manual-payment")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_admin_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> start(@PathVariable UUID id, @Valid @RequestBody PasswordInput input) {
        reauthenticate(input.password());
        return ok(withdrawals.startManual(user(), id));
    }

    @PostMapping("/admin/withdrawals/{id}/confirm-manual-payment")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_admin_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> finish(@PathVariable UUID id, @Valid @RequestBody PaymentProof input) {
        reauthenticate(input.password());
        withdrawals.finishManual(
                user(), id, input.bankReference(), input.evidenceReference(), input.note());
        return ok(true);
    }

    @PostMapping("/admin/withdrawals/{id}/{action:reject|reconcile|confirm-failed}")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "finance_admin_sensitive",
            limit = 5,
            duration = 300,
            failClosed = true)
    public ApiResponse<?> handle(
            @PathVariable UUID id,
            @PathVariable String action,
            @Valid @RequestBody DecisionInput input) {
        reauthenticate(input.password());
        withdrawals.rejectOrReconcile(
                user(), id, action.replace('-', '_').toUpperCase(Locale.ROOT), input.note());
        return ok(true);
    }

    @GetMapping("/admin/receipts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> receipts(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(
                store.rows(
                        "SELECT id,payment_id,provider,environment,provider_reference,amount,disposition,created_at FROM finance_receipts ORDER BY created_at DESC,id DESC LIMIT 25 OFFSET ?",
                        page * 25L));
    }

    @GetMapping("/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> audit(@RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(
                store.rows(
                        "SELECT id,actor_id,owner_id,subject_id,action,detail,created_at FROM finance_audit_events ORDER BY created_at DESC,id DESC LIMIT 25 OFFSET ?",
                        page * 25L));
    }

    @GetMapping("/admin/tickets")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> adminTickets(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return ok(maintenance.adminTickets(page));
    }
}
