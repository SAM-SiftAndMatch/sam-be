package com.sam.be.modules.payment.service;

public final class ProviderIdentity {
    private ProviderIdentity() {}

    public static String hash(String value) {
        return PaymentRequestKey.hash(value);
    }
}
