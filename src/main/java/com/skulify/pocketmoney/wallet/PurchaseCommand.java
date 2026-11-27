package com.skulify.pocketmoney.wallet;

import java.time.Instant;
import java.util.UUID;

/**
 * A debit instruction. The six-argument constructor is kept for direct ledger
 * callers; terminal purchases add the credential and device context.
 */
public record PurchaseCommand(
        UUID walletId,
        long amount,
        UUID merchantId,
        UUID terminalId,
        String idempotencyKey,
        UUID createdBy,
        UUID cardId,
        String cardUidHash,
        String terminalCode,
        String nonce,
        boolean offline,
        Instant requestTimestamp,
        String itemsFingerprint) {

    public PurchaseCommand(
            UUID walletId,
            long amount,
            UUID merchantId,
            UUID terminalId,
            String idempotencyKey,
            UUID createdBy) {
        this(walletId, amount, merchantId, terminalId, idempotencyKey, createdBy,
                null, null, null, null, false, null, null);
    }
}
