package com.skulify.pocketmoney.ledger;

import java.util.UUID;

public record ReconciliationResult(
        UUID walletId,
        long cachedBalance,
        long ledgerBalance,
        boolean healthy) {

    public long difference() {
        return cachedBalance - ledgerBalance;
    }
}
