package com.sam.be.modules.payment.controller;

import com.sam.be.modules.finance.PaymentReturnPolicy;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Navigation only: provider query parameters never update payment state or choose the destination.
 */
@RestController
@RequiredArgsConstructor
public class PaymentReturnController {
    private final PaymentReturnPolicy returns;

    @GetMapping("/api/v1/payments/pay2s-return")
    public ResponseEntity<Void> pay2sReturn() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(returns.resolve(null)))
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .build();
    }
}
