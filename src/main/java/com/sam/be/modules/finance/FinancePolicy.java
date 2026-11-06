package com.sam.be.modules.finance;

import java.time.*;
import java.util.Set;

public final class FinancePolicy {
    private FinancePolicy() {}

    public static final Duration WARRANTY = Duration.ofHours(168);
    public static final Set<String> BLOCKING =
            Set.of("OPEN", "IN_PROGRESS", "WAITING_CLIENT", "FIX_SUBMITTED", "DISPUTED");

    public static boolean canOpen(Instant acceptedAt, Instant now) {
        return !now.isBefore(acceptedAt) && now.isBefore(acceptedAt.plus(WARRANTY));
    }

    public static boolean canRelease(Instant end, Instant now, boolean blocked) {
        return !now.isBefore(end) && !blocked;
    }
}
