package com.sam.be.modules.finance;

import java.util.Locale;
import java.util.Set;

/** Canonical codes accepted for new receiving accounts; historical records remain unchanged. */
public final class BankCodes {
    private static final Set<String> SUPPORTED =
            Set.of(
                    "VCB", "MB", "BIDV", "CTG", "ACB", "TCB", "VPB", "TPB", "STB", "HDB", "VIB",
                    "SHB", "LPB", "MSB", "OCB", "EIB", "SEAB", "ABB", "BAB", "NAB", "SCB", "PGB",
                    "KLB", "SHBVN", "CAKE", "UBANK", "PVCB", "BVB", "NCB", "SGB", "VAB", "DAB",
                    "GPB", "HLBVN", "CIMB", "UOB", "WOO", "IVB", "VRB", "COOPB");

    private BankCodes() {}

    public static String canonical(String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        return "MBB".equals(normalized) ? "MB" : normalized;
    }

    public static boolean isSupported(String code) {
        return SUPPORTED.contains(canonical(code));
    }
}
