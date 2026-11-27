package com.skulify.pocketmoney.ledger;

import java.util.Set;

/** Double-entry accounts used by Phase 1. Every posting names one of these. */
public final class AccountCode {

    public static final String PARENT_FUNDS_IN = "PARENT_FUNDS_IN";
    public static final String STUDENT_WALLET = "STUDENT_WALLET";
    public static final String SHOP_SETTLEMENT = "SHOP_SETTLEMENT";
    public static final String PLATFORM_REVENUE = "PLATFORM_REVENUE";
    public static final String CASH_OUT_FLOAT = "CASH_OUT_FLOAT";

    private static final Set<String> KNOWN = Set.of(
            PARENT_FUNDS_IN, STUDENT_WALLET, SHOP_SETTLEMENT, PLATFORM_REVENUE, CASH_OUT_FLOAT);

    private AccountCode() {
    }

    public static boolean isKnown(String code) {
        return code != null && KNOWN.contains(code);
    }
}
