package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.sam.be.infrastructure.thirdparty.vnpay.VnpayProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentReturnPolicyTest {
    PaymentReturnPolicy policy() {
        var legacy = new VnpayProperties();
        legacy.setAllowedReturnUrls(List.of("https://sam.example/client/payment/vnpay-return"));
        return new PaymentReturnPolicy(
                legacy,
                "https://sam.example/client/payment/vnpay-return",
                "https://sam.example",
                "LIVE");
    }

    @Test
    void allowsKnownReturnAndOwnedWorkspacePath() {
        assertEquals("https://sam.example/client/payment/vnpay-return", policy().resolve(null));
        String target =
                "https://sam.example/workspace/00000000-0000-0000-0000-000000000001/contract";
        assertEquals(target, policy().resolve(target));
    }

    @Test
    void rejectsExternalOriginUserinfoFragmentsAndOpenRedirectParameters() {
        for (String target :
                List.of(
                        "https://evil.example/client/payment/vnpay-return",
                        "https://sam.example@evil.example/client/payment/vnpay-return",
                        "https://sam.example/client/payment/vnpay-return#secret",
                        "https://sam.example/client/payment/vnpay-return?next=https://evil.example",
                        "http://sam.example/client/payment/vnpay-return",
                        "javascript:alert(1)",
                        "https://sam.example/redirect"))
            assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> policy().resolve(target),
                    target);
    }
}
