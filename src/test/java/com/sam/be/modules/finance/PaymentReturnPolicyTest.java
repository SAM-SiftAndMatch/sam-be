package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentReturnPolicyTest {
    PaymentReturnPolicy policy() {
        return new PaymentReturnPolicy(
                "https://sam.example/payment/return", "https://sam.example", "LIVE");
    }

    @Test
    void allowsKnownReturnAndOwnedWorkspacePath() {
        assertEquals("https://sam.example/payment/return", policy().resolve(null));
        String target =
                "https://sam.example/workspace/00000000-0000-0000-0000-000000000001/contract";
        assertEquals(target, policy().resolve(target));
    }

    @Test
    void rejectsExternalOriginUserinfoFragmentsAndOpenRedirectParameters() {
        for (String target :
                List.of(
                        "https://evil.example/payment/return",
                        "https://sam.example@evil.example/payment/return",
                        "https://sam.example/payment/return#secret",
                        "https://sam.example/payment/return?next=https://evil.example",
                        "http://sam.example/payment/return",
                        "javascript:alert(1)",
                        "https://sam.example/redirect"))
            assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> policy().resolve(target),
                    target);
    }
}
