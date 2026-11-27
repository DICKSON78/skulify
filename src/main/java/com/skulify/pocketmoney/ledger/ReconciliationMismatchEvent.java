package com.skulify.pocketmoney.ledger;

import java.util.UUID;

/** Published whenever a cached wallet balance disagrees with the ledger. */
public record ReconciliationMismatchEvent(
        UUID walletId,
        long cachedBalance,
        long ledgerBalance) {
}
